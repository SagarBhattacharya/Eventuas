import { CONFIG, ACTORS, session, saveSession, log, logListeners, api, health,
  loginWithPassword, loginWithRedirect, completeRedirectLogin, logout, jwtExpiry } from "./api.js";
import { SCENARIOS, missingLogins } from "./scenarios.js";

const $app = document.getElementById("app");
const money = (p, c) => `${(p / 100).toLocaleString(undefined, { minimumFractionDigits: 2 })} ${c}`;
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const fmt = (d) => (d ? new Date(d).toLocaleString() : "-");
const localInput = (ms) => { const d = new Date(ms - new Date().getTimezoneOffset() * 6e4); return d.toISOString().slice(0, 16); };
const uuid = () => crypto.randomUUID();

const S = {
  tab: "organizer", busy: false, health: { api: null, auth: null }, flash: "",
  form: {
    name: "Systems & Rust Kolkata", venue: "Biswa Bangla Convention Centre", description: "Storage engines, io_uring and JVM internals.",
    start: localInput(Date.now() + 36e5), end: localInput(Date.now() + 6 * 36e5),
    tier: "Early Bird", qty: "5", price: "499", currency: "INR", staffEmail: "", q: "", qr: "", scanInput: "",
    login: { organizer: "", buyer: "", scanner: "" },
  },
  myEvents: [], selected: null, tiers: [], stats: null, staff: [],
  feed: [], detail: null, wallet: [], lastKey: null, qrFor: "", scan: null, scenario: {}, running: null,
};

const authed = (a) => !!session[a]?.token;
const err = (res) => (res.data?.detail ? `${res.status} ${res.data.code || ""}: ${res.data.detail}` : `${res.status}`);
const toast = (m) => { S.flash = m; render(); };

async function act(fn) {
  if (S.busy) return;
  S.busy = true; S.flash = ""; render();
  try { await fn(); } catch (e) { S.flash = e.message; }
  S.busy = false; render();
}

// ---------- data loaders ----------
async function loadMine() {
  if (!authed("organizer")) return;
  const r = await api("GET", "/events?size=50", { actor: "organizer" });
  if (r.ok) S.myEvents = r.data.items;
  if (S.selected) await loadSelected(S.selected);
}
async function loadSelected(id) {
  S.selected = id; S.stats = null;
  const [t, s] = await Promise.all([api("GET", `/events/${id}/tiers`, { actor: "organizer" }), api("GET", `/events/${id}/staff`, { actor: "organizer" })]);
  S.tiers = t.ok ? t.data : []; S.staff = s.ok ? s.data : [];
}
async function loadFeed() {
  const r = await api("GET", `/events/published?size=30${S.form.q ? "&q=" + encodeURIComponent(S.form.q) : ""}`);
  if (r.ok) S.feed = r.data.items;
}
async function loadWallet() {
  if (!authed("buyer")) return;
  const r = await api("GET", "/tickets/me?size=50", { actor: "buyer" });
  if (r.ok) S.wallet = r.data.items;
}

// ---------- actions (dispatched by data-act) ----------
const actions = {
  async login({ actor }) { const f = S.form.login[actor]; await loginWithPassword(actor, f || ACTORS[actor].user, ACTORS[actor].pass); await afterLogin(actor); },
  async loginRedirect({ actor }) { await loginWithRedirect(actor); },
  logout({ actor }) { logout(actor); },
  async loginAll() { for (const a of Object.keys(ACTORS)) { await loginWithPassword(a, ACTORS[a].user, ACTORS[a].pass); } await afterLogin(); },
  tab({ tab }) { S.tab = tab; if (tab === "buyer") return Promise.all([loadFeed(), loadWallet()]); if (tab === "organizer") return loadMine(); },
  async refresh() {
    S.health = await health();
    await Promise.all([loadMine(), loadFeed(), loadWallet()]);
  },

  async createEvent() {
    const f = S.form;
    const r = await api("POST", "/events", { actor: "organizer", body: { name: f.name, venue: f.venue, description: f.description,
      startDate: new Date(f.start).toISOString(), endDate: new Date(f.end).toISOString() } });
    if (!r.ok) return toast(err(r));
    S.selected = r.data.id; await loadMine();
  },
  async select({ id }) { await loadSelected(id); },
  async addTier() {
    const f = S.form;
    const r = await api("POST", `/events/${S.selected}/tiers`, { actor: "organizer", body: { tier: f.tier, description: `${f.tier} pass`,
      availableQty: Number(f.qty), price: Math.round(Number(f.price) * 100), currency: f.currency.toUpperCase() } });
    if (!r.ok) return toast(err(r));
    await loadSelected(S.selected);
  },
  async closeTier({ id }) { const r = await api("POST", `/events/${S.selected}/tiers/${id}/close`, { actor: "organizer" }); if (!r.ok) toast(err(r)); await loadSelected(S.selected); },
  async publish() { const r = await api("POST", `/events/${S.selected}/publish`, { actor: "organizer" }); if (!r.ok) toast(err(r)); await loadMine(); },
  async closeEvent() { const r = await api("POST", `/events/${S.selected}/close`, { actor: "organizer" }); if (!r.ok) toast(err(r)); await loadMine(); },
  async stats() { const r = await api("GET", `/events/${S.selected}/stats`, { actor: "organizer" }); S.stats = r.data; },
  async assignStaff() {
    const email = S.form.staffEmail || session.scanner?.me?.email;
    if (!email) return toast("Enter an email or sign in as the scanner first");
    const r = await api("POST", `/events/${S.selected}/staff`, { actor: "organizer", body: { email } });
    if (!r.ok) toast(err(r)); await loadSelected(S.selected);
  },
  async revokeStaff({ id }) { await api("DELETE", `/events/${S.selected}/staff/${id}`, { actor: "organizer" }); await loadSelected(S.selected); },

  async search() { await loadFeed(); },
  async detail({ id }) { const r = await api("GET", `/events/published/${id}`); S.detail = r.ok ? r.data : null; },
  async buy({ tier, reuse }) {
    const key = reuse && S.lastKey?.tier === tier ? S.lastKey.key : `idem-${uuid()}`;
    const r = await api("POST", `/tiers/${tier}/buy`, { actor: "buyer", headers: { "Idempotency-Key": key }, label: reuse ? "Retry with same Idempotency-Key" : "Buy ticket" });
    if (!r.ok) return toast(err(r));
    const same = reuse && S.lastKey?.ticket === r.data.id;
    S.lastKey = { tier, key, ticket: r.data.id };
    S.flash = reuse ? (same ? "Replay returned the same ticket (no double purchase)." : "Replay returned a DIFFERENT ticket, idempotency is broken.") : "";
    await Promise.all([loadWallet(), S.detail && actions.detail({ id: S.detail.id })]);
  },
  async qr({ id }) {
    const r = await api("GET", `/tickets/${id}/qr`, { actor: "buyer" });
    if (r.ok) { S.form.qr = r.data.qrToken; S.qrFor = id; S.form.scanInput = r.data.qrToken; } else toast(err(r));
  },
  async scan() {
    const r = await api("POST", "/check-ins", { actor: "scanner", body: { qrToken: S.form.scanInput.trim() } });
    S.scan = r.ok ? { ok: true, text: `201 Check-in approved at ${fmt(r.data.checkedInAt)}` } : { ok: false, text: err(r) };
    await loadWallet();
  },

  async runScenario({ id }) {
    const sc = SCENARIOS.find((x) => x.id === id);
    S.running = id; S.scenario[id] = null; render();
    S.scenario[id] = await sc.run(); S.running = null;
    await Promise.all([loadMine(), loadFeed(), loadWallet()]);
  },
  clearLog() { log.length = 0; },
  reset() { sessionStorage.clear(); location.href = CONFIG.redirectUri; },
};

async function afterLogin(actor) {
  const me = session.scanner?.me;
  if (me && !S.form.staffEmail) S.form.staffEmail = me.email;
  await Promise.all([loadMine(), loadFeed(), loadWallet()]);
}

// ---------- views ----------
const badge = (s) => `<span class="pill ${s === "ACTIVE" ? "up" : s === "DRAFT" ? "warn" : "neutral"}">${esc(s)}</span>`;
const bind = (k, extra = "") => `data-bind="${k}" value="${esc(S.form[k])}" ${extra}`;

function actorCard(a) {
  const meta = ACTORS[a], s = session[a], exp = s?.token ? jwtExpiry(s.token) : null;
  const expired = exp && exp < Date.now();
  return `<div class="card actor ${S.tab === a ? "active" : ""}">
    <div class="row between"><span class="who">${meta.label}</span>
      <span class="pill ${s?.token && !expired ? "up" : "down"}">${s?.token ? (expired ? "expired" : "signed in") : "signed out"}</span></div>
    ${s?.me ? `<div>${esc(s.me.name)}<div class="muted mono">${esc(s.me.email)}</div></div>` : `<div class="muted">Seeded user <b>${meta.user}</b> / ${meta.pass}</div>`}
    <div class="row">${s?.token
      ? `<button class="ghost sm" data-act="logout" data-actor="${a}">Sign out</button>`
      : `<button class="sm" data-act="login" data-actor="${a}">Sign in as ${meta.user}</button>
         <button class="ghost sm" data-act="loginRedirect" data-actor="${a}" title="Real OIDC login page with PKCE">via Casdoor page</button>`}</div></div>`;
}

const needLogin = (a) => `<div class="callout">Sign in as <b>${ACTORS[a].label}</b> (left panel) to use this tab.</div>`;

function organizerView() {
  if (!authed("organizer")) return needLogin("organizer");
  const f = S.form, ev = S.myEvents.find((e) => e.id === S.selected);
  return `<div class="stack">
  <div class="card stack"><h2>New draft event</h2>
    <div class="form">
      <label class="wide">Name<input ${bind("name")}></label>
      <label>Venue<input ${bind("venue")}></label>
      <label>Start<input type="datetime-local" ${bind("start")}></label>
      <label>End<input type="datetime-local" ${bind("end")}></label>
      <label class="wide">Description<input ${bind("description")}></label>
    </div><div><button data-act="createEvent">Create draft</button></div></div>

  <div class="card stack"><h2>My events <span class="muted">(${S.myEvents.length})</span></h2>
    ${S.myEvents.length ? S.myEvents.map((e) => `<div class="item ${e.id === S.selected ? "sel" : ""}">
      <div class="row between"><b>${esc(e.name)}</b>${badge(e.status)}</div>
      <div class="muted">${esc(e.venue)} · ${fmt(e.startDate)}</div>
      <div><button class="ghost sm" data-act="select" data-id="${e.id}">${e.id === S.selected ? "Selected" : "Manage"}</button></div></div>`).join("") : `<div class="empty">No events yet</div>`}</div>

  ${ev ? `<div class="card stack"><div class="row between"><h2>Manage: ${esc(ev.name)}</h2>${badge(ev.status)}</div>
    <div class="row">
      <button data-act="publish" ${ev.status !== "DRAFT" ? "disabled" : ""}>Publish</button>
      <button class="danger" data-act="closeEvent" ${ev.status === "CLOSED" ? "disabled" : ""}>Close event</button>
      <button class="ghost" data-act="stats">Stats</button></div>
    ${S.stats ? `<pre>${esc(JSON.stringify(S.stats, null, 2))}</pre>` : ""}
    <h3>Ticket tiers</h3>
    ${S.tiers.length ? `<table><tr><th>Tier</th><th>Price</th><th>Left</th><th>Status</th><th></th></tr>${S.tiers.map((t) => `<tr>
      <td>${esc(t.tier)}</td><td>${money(t.price, t.currency)}</td><td>${t.availableQty}</td><td>${badge(t.status)}</td>
      <td>${t.status === "ACTIVE" ? `<button class="ghost sm" data-act="closeTier" data-id="${t.id}">Close</button>` : ""}</td></tr>`).join("")}</table>` : `<div class="empty">No tiers (publish needs at least one)</div>`}
    <div class="form"><label>Tier name<input ${bind("tier")}></label><label>Qty<input type="number" ${bind("qty")}></label>
      <label>Price (major units)<input type="number" ${bind("price")}></label><label>Currency<input ${bind("currency")}></label>
      <div><button data-act="addTier" ${ev.status === "CLOSED" ? "disabled" : ""}>Add tier</button></div></div>
    <h3>Gate staff</h3>
    ${S.staff.length ? `<table>${S.staff.map((m) => `<tr><td>${esc(m.name)}</td><td class="mono">${esc(m.email)}</td>
      <td><button class="ghost sm" data-act="revokeStaff" data-id="${m.userId}">Revoke</button></td></tr>`).join("")}</table>` : `<div class="empty">No staff assigned</div>`}
    <div class="row"><input class="grow" placeholder="staff email (defaults to scanner)" ${bind("staffEmail")}><button data-act="assignStaff">Assign</button></div>
  </div>` : ""}</div>`;
}

function buyerView() {
  if (!authed("buyer")) return needLogin("buyer");
  return `<div class="stack">
  <div class="card stack"><div class="row"><h2 class="grow">Published events</h2>
    <input style="max-width:220px" placeholder="search…" ${bind("q")}><button class="ghost" data-act="search">Search</button></div>
    ${S.feed.length ? S.feed.map((e) => `<div class="item"><div class="row between"><b>${esc(e.name)}</b>${badge(e.status)}</div>
      <div class="muted">${esc(e.venue)} · ${fmt(e.startDate)}</div>
      <div><button class="ghost sm" data-act="detail" data-id="${e.id}">View tiers</button></div>
      ${S.detail?.id === e.id ? `<table>${S.detail.tiers.map((t) => `<tr><td>${esc(t.tier)}</td><td>${money(t.price, t.currency)}</td><td>${t.availableQty} left</td>
        <td class="row"><button class="sm" data-act="buy" data-tier="${t.id}" ${t.availableQty < 1 ? "disabled" : ""}>Buy</button>
        ${S.lastKey?.tier === t.id ? `<button class="ghost sm" data-act="buy" data-tier="${t.id}" data-reuse="1" title="Resends the previous Idempotency-Key">Retry same key</button>` : ""}</td></tr>`).join("")}</table>` : ""}</div>`).join("")
      : `<div class="empty">Nothing published yet. Publish an event as the organizer.</div>`}</div>

  <div class="card stack"><h2>My tickets <span class="muted">(${S.wallet.length})</span></h2>
    ${S.wallet.length ? S.wallet.map((t) => `<div class="item"><div class="row between"><b>${esc(t.eventName)}</b>
      <span class="pill ${t.checkedIn ? "ok" : "neutral"}">${t.checkedIn ? "checked in" : "valid"}</span></div>
      <div class="muted">${esc(t.tierName)} · ${money(t.price, t.currency)} · bought ${fmt(t.purchasedAt)}</div>
      <div><button class="ghost sm" data-act="qr" data-id="${t.ticketId}">Show QR token</button></div>
      ${S.qrFor === t.ticketId ? `<pre>${esc(S.form.qr)}</pre>` : ""}</div>`).join("") : `<div class="empty">No tickets yet</div>`}</div></div>`;
}

function scannerView() {
  if (!authed("scanner")) return needLogin("scanner");
  return `<div class="card stack"><h2>Gate scanner</h2>
    <p class="muted">Paste a QR token (the Buyer tab's <i>Show QR token</i> fills this in automatically). The scanner must be assigned as staff.</p>
    <textarea data-bind="scanInput" placeholder="uuid.signature">${esc(S.form.scanInput)}</textarea>
    <div class="row"><button data-act="scan" ${S.form.scanInput ? "" : "disabled"}>Check in</button></div>
    ${S.scan ? `<div class="result ${S.scan.ok ? "ok" : "bad"}">${esc(S.scan.text)}</div>` : ""}</div>`;
}

function scenariosView() {
  const miss = missingLogins();
  return `<div class="stack">
  ${miss.length ? `<div class="callout">Sign in as ${miss.join(", ")} first. <button class="sm" data-act="loginAll">Sign in all three</button></div>` : ""}
  ${SCENARIOS.map((sc) => `<div class="card stack"><div class="row between"><div><h2>${sc.title}</h2><div class="muted">${sc.desc}</div></div>
    <button data-act="runScenario" data-id="${sc.id}" ${miss.length || S.running ? "disabled" : ""}>${S.running === sc.id ? "Running…" : "Run"}</button></div>
    ${S.scenario[sc.id] ? `<ul class="steps">${S.scenario[sc.id].map((s) => `<li><span class="tick ${s.pass ? "ok" : "bad"}">${s.pass ? "✓" : "✗"}</span>
      <span class="grow">${esc(s.name)}</span><span class="muted mono">${s.status}${s.pass ? "" : ` (expected ${esc(s.expected)})`}</span></li>`).join("")}</ul>
      <div class="pill ${S.scenario[sc.id].every((s) => s.pass) ? "ok" : "bad"}" style="justify-self:start">${S.scenario[sc.id].filter((s) => s.pass).length}/${S.scenario[sc.id].length} passed</div>` : ""}</div>`).join("")}</div>`;
}

function logView() {
  return `<div class="card stack logcol"><div class="row between"><h2>Request log</h2><button class="ghost sm" data-act="clearLog">Clear</button></div>
    <div class="log">${log.length ? log.map((e) => `<details><summary><span class="code s${String(e.status)[0]}">${e.status || "ERR"}</span>
      <b class="mono">${e.method}</b><span class="path">${esc(e.path)}</span><span class="muted mono">${e.ms}ms</span></summary>
      <div class="muted mono">${e.actor ? "as " + e.actor : "anonymous"}${e.label ? " · " + esc(e.label) : ""}${e.requestId ? " · " + esc(e.requestId) : ""}</div>
      ${e.req !== undefined ? `<pre>${esc(JSON.stringify(e.req, null, 2))}</pre>` : ""}<pre>${esc(JSON.stringify(e.res, null, 2))}</pre></details>`).join("")
      : `<div class="empty">Requests appear here</div>`}</div></div>`;
}

function render() {
  const keep = document.activeElement?.dataset?.bind; // restore focus after re-render
  const sel = [document.activeElement?.selectionStart, document.activeElement?.selectionEnd];
  const views = { organizer: organizerView, buyer: buyerView, scanner: scannerView, scenarios: scenariosView };
  $app.innerHTML = `
  <header class="top"><div class="row"><h1>Eventuas Test Console</h1>
    <span class="pills"><span class="pill ${S.health.api ? "up" : "down"}">API ${S.health.api ? "up" : "down"}</span>
    <span class="pill ${S.health.auth ? "up" : "down"}">Casdoor ${S.health.auth ? "up" : "down"}</span></span></div>
    <div class="row"><button class="ghost" data-act="refresh">Refresh</button><button class="ghost" data-act="reset">Reset</button></div></header>
  <div class="layout">
    <aside class="stack">${Object.keys(ACTORS).map(actorCard).join("")}
      <div class="card muted">API <span class="mono">${esc(CONFIG.apiUrl)}</span><br>Auth <span class="mono">${esc(CONFIG.authUrl)}</span></div></aside>
    <main><div class="tabs">${[["organizer", "Organizer"], ["buyer", "Buyer"], ["scanner", "Scanner"], ["scenarios", "Scenarios"]]
      .map(([k, l]) => `<button class="tab ${S.tab === k ? "on" : ""}" data-act="tab" data-tab="${k}">${l}</button>`).join("")}</div>
      ${S.flash ? `<div class="callout" style="margin-bottom:12px">${esc(S.flash)}</div>` : ""}
      ${(!S.health.api && S.health.api !== null) ? `<div class="callout" style="margin-bottom:12px">Backend not reachable at ${esc(CONFIG.apiUrl)}. Start it with <span class="mono">./gradlew bootRun</span> (see README).</div>` : ""}
      ${views[S.tab]()}</main>
    ${logView()}</div>`;
  if (keep) { const el = $app.querySelector(`[data-bind="${keep}"]`); if (el) { el.focus(); try { el.setSelectionRange(...sel); } catch {} } }
}

// ---------- events ----------
$app.addEventListener("input", (e) => {
  const k = e.target.dataset?.bind;
  if (k) S.form[k] = e.target.value;
});
$app.addEventListener("click", (e) => {
  const el = e.target.closest("[data-act]");
  if (!el) return;
  const fn = actions[el.dataset.act];
  if (!fn) return;
  const args = { ...el.dataset, reuse: !!el.dataset.reuse };
  act(() => fn(args));
});
$app.addEventListener("keydown", (e) => { if (e.key === "Enter" && e.target.dataset?.bind === "q") act(actions.search); });
logListeners.add(render);

(async function boot() {
  render();
  try { const a = await completeRedirectLogin(); if (a) S.tab = a === "scanner" ? "scanner" : a; } catch (e) { S.flash = `Login failed: ${e.message}`; }
  S.health = await health(); render();
  await act(() => afterLogin());
})();
