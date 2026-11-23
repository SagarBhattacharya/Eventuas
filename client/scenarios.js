// One-click scenarios: each step records expected vs actual HTTP status.
import { api, session } from "./api.js";

const ok2xx = (s) => s >= 200 && s < 300;
const uid = () => crypto.randomUUID();
const stamp = () => new Date().toISOString().slice(11, 19);

export const SCENARIOS = [
  { id: "happy", title: "Happy path", desc: "Create, publish, buy, replay purchase, scan, rescan, close.", run: happy },
  { id: "race", title: "Oversell race", desc: "6 buyers race for 1 ticket. Exactly one must win.", run: race },
  { id: "authz", title: "Authorization", desc: "Draft is hidden, buyer cannot scan or edit someone else's event.", run: authz },
];

export function missingLogins() {
  return Object.keys({ organizer: 1, buyer: 1, scanner: 1 }).filter((a) => !session[a]?.token);
}

async function liveEvent(qty, tag) {
  const start = new Date(Date.now() + 3600e3), end = new Date(start.getTime() + 5 * 3600e3);
  const ev = await api("POST", "/events", { actor: "organizer", label: tag, body: {
    name: `${tag} ${stamp()}`, description: "Created by a scenario", venue: "Test Hall",
    startDate: start.toISOString(), endDate: end.toISOString() } });
  if (!ev.ok) return { ev };
  const id = ev.data.id;
  const tier = await api("POST", `/events/${id}/tiers`, { actor: "organizer", label: tag, body: {
    tier: "Scenario", description: "Scenario tier", availableQty: qty, price: 49900, currency: "INR" } });
  const staff = await api("POST", `/events/${id}/staff`, { actor: "organizer", label: tag,
    body: { email: session.scanner.me.email } });
  const pub = await api("POST", `/events/${id}/publish`, { actor: "organizer", label: tag });
  return { ev, tier, staff, pub, id, tierId: tier.data?.id };
}

function recorder() {
  const steps = [];
  return {
    steps,
    check(name, res, expected, extra = true) {
      const pass = (typeof expected === "function" ? expected(res.status) : res.status === expected) && extra;
      steps.push({ name, status: res.status, pass, expected: typeof expected === "function" ? "2xx" : expected });
      return res;
    },
    assert(name, pass, detail = "") { steps.push({ name, status: "", pass, expected: detail }); },
  };
}

async function happy() {
  const r = recorder();
  const s = await liveEvent(2, "Happy path");
  r.check("Create event", s.ev, 201);
  if (!s.id) return r.steps;
  r.check("Add tier", s.tier, 201);
  r.check("Assign scanner as staff", s.staff, ok2xx);
  r.check("Publish event", s.pub, 200);
  const feed = await api("GET", `/events/published/${s.id}`, { label: "Public detail (no auth)" });
  r.check("Public can see event", feed, 200);

  const key = `idem-${uid()}`;
  const buy = r.check("Buy ticket", await api("POST", `/tiers/${s.tierId}/buy`, { actor: "buyer", headers: { "Idempotency-Key": key } }), ok2xx);
  const again = await api("POST", `/tiers/${s.tierId}/buy`, { actor: "buyer", headers: { "Idempotency-Key": key }, label: "Replay same Idempotency-Key" });
  r.check("Replay with same key returns same ticket", again, ok2xx, again.data?.id === buy.data?.id);
  const tiers = await api("GET", `/events/${s.id}/tiers`, { actor: "organizer" });
  r.assert("Stock decremented only once", tiers.data?.[0]?.availableQty === 1, "availableQty = 1");

  const qr = r.check("Fetch QR token", await api("GET", `/tickets/${buy.data?.id}/qr`, { actor: "buyer" }), 200);
  const token = qr.data?.qrToken;
  r.check("Scanner checks in", await api("POST", "/check-ins", { actor: "scanner", body: { qrToken: token } }), 201);
  r.check("Second scan rejected", await api("POST", "/check-ins", { actor: "scanner", body: { qrToken: token } }), 409);
  r.check("Close event", await api("POST", `/events/${s.id}/close`, { actor: "organizer" }), 200);
  const after = await api("POST", `/tiers/${s.tierId}/buy`, { actor: "buyer", headers: { "Idempotency-Key": `idem-${uid()}` } });
  r.check("Buying after close rejected", after, (c) => c >= 400 && c < 500);
  return r.steps;
}

async function race() {
  const r = recorder();
  const s = await liveEvent(1, "Race");
  r.check("Setup: create event", s.ev, 201);
  if (!s.id) return r.steps;
  r.check("Setup: publish", s.pub, 200);
  const results = await Promise.all(Array.from({ length: 6 }, () =>
    api("POST", `/tiers/${s.tierId}/buy`, { actor: "buyer", headers: { "Idempotency-Key": `idem-${uid()}` }, label: "Race buy" })));
  const wins = results.filter((x) => ok2xx(x.status)).length;
  const losses = results.filter((x) => x.status === 409).length;
  r.assert("Exactly one purchase succeeded", wins === 1, `got ${wins}`);
  r.assert("All others got 409 sold out", losses === 5, `got ${losses}`);
  const tiers = await api("GET", `/events/${s.id}/tiers`, { actor: "organizer" });
  r.assert("Stock is 0 (never negative)", tiers.data?.[0]?.availableQty === 0, `qty ${tiers.data?.[0]?.availableQty}`);
  return r.steps;
}

async function authz() {
  const r = recorder();
  const s = await liveEvent(5, "Authz");
  r.check("Setup: create event", s.ev, 201);
  if (!s.id) return r.steps;
  const draft = await api("POST", "/events", { actor: "organizer", label: "Draft for authz", body: {
    name: `Draft ${stamp()}`, description: "d", venue: "v",
    startDate: new Date(Date.now() + 864e5).toISOString(), endDate: new Date(Date.now() + 9e7).toISOString() } });
  r.check("Draft hidden from public", await api("GET", `/events/published/${draft.data?.id}`), 404);
  r.check("Buyer cannot publish organizer's event", await api("POST", `/events/${draft.data?.id}/publish`, { actor: "buyer" }), 403);
  r.check("Buyer cannot add tiers", await api("POST", `/events/${s.id}/tiers`, { actor: "buyer", body: {
    tier: "X", description: "x", availableQty: 1, price: 1, currency: "INR" } }), 403);
  r.check("Anonymous cannot list own events", await api("GET", "/events"), 401);
  const buy = await api("POST", `/tiers/${s.tierId}/buy`, { actor: "buyer", headers: { "Idempotency-Key": `idem-${uid()}` } });
  const qr = await api("GET", `/tickets/${buy.data?.id}/qr`, { actor: "buyer" });
  r.check("Scanner cannot read buyer's QR", await api("GET", `/tickets/${buy.data?.id}/qr`, { actor: "scanner" }), 403);
  r.check("Buyer (not staff) cannot check in", await api("POST", "/check-ins", { actor: "buyer", body: { qrToken: qr.data?.qrToken } }), 403);
  r.check("Tampered QR rejected", await api("POST", "/check-ins", { actor: "scanner", body: { qrToken: (qr.data?.qrToken || "x.y").slice(0, -2) + "AA" } }), 400);
  r.check("Missing Idempotency-Key rejected", await api("POST", `/tiers/${s.tierId}/buy`, { actor: "buyer" }), (c) => c >= 400 && c < 500);
  return r.steps;
}
