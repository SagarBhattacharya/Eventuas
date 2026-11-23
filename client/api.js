// Config, auth (password grant + PKCE redirect) and the API wrapper. No dependencies.
export const CONFIG = {
  apiUrl: "http://localhost:8080",
  authUrl: "http://localhost:9090",
  clientId: "eventuas-client-id",
  clientSecret: "eventuas-client-secret", // seeded dev value from deploy/init_data.json
  redirectUri: `${location.origin}/`,
};

export const ACTORS = {
  organizer: { label: "Organizer", user: "aarav", pass: "123" },
  buyer: { label: "Buyer", user: "meera", pass: "123" },
  scanner: { label: "Scanner", user: "rohan", pass: "123" },
};

const SKEY = "eventuas.session.v2";
export const session = (() => {
  try { return JSON.parse(sessionStorage.getItem(SKEY)) || {}; } catch { return {}; }
})(); // { organizer: {token, me}, ... }
export const saveSession = () => sessionStorage.setItem(SKEY, JSON.stringify(session));

export const log = []; // newest first
export const logListeners = new Set();

export function jwtExpiry(token) {
  try {
    const p = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return p.exp ? p.exp * 1000 : null;
  } catch { return null; }
}

async function tokenRequest(params) {
  const body = new URLSearchParams({
    client_id: CONFIG.clientId, client_secret: CONFIG.clientSecret, ...params,
  });
  const r = await fetch(`${CONFIG.authUrl}/api/login/oauth/access_token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok || !data.access_token) {
    throw new Error(data.error_description || data.error || `Token endpoint returned ${r.status}`);
  }
  return data.access_token;
}

async function adopt(actor, token) {
  session[actor] = { token, me: null };
  const res = await api("GET", "/users/me", { actor, label: `Provision ${actor}` });
  if (!res.ok) { delete session[actor]; saveSession(); throw new Error("API rejected the token (is the backend running?)"); }
  session[actor].me = res.data;
  saveSession();
}

export async function loginWithPassword(actor, user, pass) {
  const token = await tokenRequest({ grant_type: "password", username: user, password: pass, scope: "openid profile email" });
  await adopt(actor, token);
}

export function logout(actor) { delete session[actor]; saveSession(); }

// ---- PKCE redirect flow (real OIDC login page) ----
const b64url = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

export async function loginWithRedirect(actor) {
  const verifier = b64url(crypto.getRandomValues(new Uint8Array(32)));
  const challenge = b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  const state = `${actor}.${b64url(crypto.getRandomValues(new Uint8Array(12)))}`;
  sessionStorage.setItem("eventuas.pkce", JSON.stringify({ verifier, state }));
  const q = new URLSearchParams({
    client_id: CONFIG.clientId, response_type: "code", redirect_uri: CONFIG.redirectUri,
    scope: "openid profile email", state, code_challenge: challenge, code_challenge_method: "S256", prompt: "login",
  });
  location.href = `${CONFIG.authUrl}/login/oauth/authorize?${q}`;
}

export async function completeRedirectLogin() {
  const p = new URLSearchParams(location.search);
  if (!p.get("code") || !p.get("state")) return null;
  history.replaceState({}, "", CONFIG.redirectUri);
  const saved = JSON.parse(sessionStorage.getItem("eventuas.pkce") || "null");
  sessionStorage.removeItem("eventuas.pkce");
  if (!saved || saved.state !== p.get("state")) throw new Error("OIDC state mismatch");
  const actor = saved.state.split(".")[0];
  const token = await tokenRequest({
    grant_type: "authorization_code", code: p.get("code"),
    redirect_uri: CONFIG.redirectUri, code_verifier: saved.verifier,
  });
  await adopt(actor, token);
  return actor;
}

// ---- API wrapper ----
export async function api(method, path, { actor = null, body, headers = {}, label = "" } = {}) {
  const h = { Accept: "application/json", ...headers };
  const token = actor && session[actor]?.token;
  if (token) h.Authorization = `Bearer ${token}`;
  if (body !== undefined) h["Content-Type"] = "application/json";
  const entry = { id: crypto.randomUUID(), method, path, actor, label, status: 0, ms: 0, requestId: "", req: body, res: null, at: new Date() };
  const t0 = performance.now();
  try {
    const r = await fetch(CONFIG.apiUrl + path, { method, headers: h, body: body !== undefined ? JSON.stringify(body) : undefined });
    const text = await r.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    Object.assign(entry, { status: r.status, requestId: r.headers.get("X-Request-Id") || "", res: data });
    if (r.status === 401 && actor) entry.label ||= "Token rejected or expired, sign in again";
    entry.ms = Math.round(performance.now() - t0);
    push(entry);
    return { ok: r.ok, status: r.status, data };
  } catch (e) {
    entry.ms = Math.round(performance.now() - t0);
    entry.res = { error: "Network error, is the API running on " + CONFIG.apiUrl + "? (" + e.message + ")" };
    push(entry);
    return { ok: false, status: 0, data: entry.res };
  }
}

function push(entry) {
  log.unshift(entry);
  if (log.length > 60) log.pop();
  logListeners.forEach((f) => f());
}

export async function health() {
  const ping = (u) => fetch(u).then((r) => r.ok).catch(() => false);
  return { api: await ping(`${CONFIG.apiUrl}/actuator/health`), auth: await ping(`${CONFIG.authUrl}/api/health`) };
}
