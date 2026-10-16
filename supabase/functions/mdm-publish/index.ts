import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: cors });
function b64(bytes: Uint8Array): string { let s = ""; for (const b of bytes) s += String.fromCharCode(b); return btoa(s); }
function fromB64(s: string): Uint8Array { return Uint8Array.from(atob(s), c => c.charCodeAt(0)); }
async function derive(password: string, salt: Uint8Array, iterations: number): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  return new Uint8Array(await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations }, key, 256));
}
function equal(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let diff = 0; for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i]; return diff === 0;
}
Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({ error: "method_not_allowed" }, 405);
  const url = Deno.env.get("SUPABASE_URL"), service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !service) return reply({ error: "service_unavailable" }, 503);
  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return reply({ error: "invalid_json" }, 400); }
  const password = String(body.password || "");
  if (!password || password.length > 256) return reply({ error: "invalid_password" }, 401);
  const secretRes = await fetch(url + "/rest/v1/mdm_secret?select=password_salt,password_hash,iterations&id=eq.1&limit=1", {
    headers: { apikey: service, Authorization: "Bearer " + service, "Cache-Control": "no-store" }
  });
  if (!secretRes.ok) return reply({ error: "password_check_failed" }, 502);
  const secrets = await secretRes.json();
  if (!Array.isArray(secrets) || !secrets.length) return reply({ error: "password_not_configured" }, 409);
  const secret = secrets[0];
  const actual = await derive(password, fromB64(secret.password_salt), Number(secret.iterations));
  if (!equal(actual, fromB64(secret.password_hash))) return reply({ error: "invalid_password" }, 401);

  if (body.action === "list") {
    const stateRes = await fetch(url + "/rest/v1/data_sync_state?select=revision,snapshot_version,published_at&id=eq.1", {
      headers: { apikey: service, Authorization: "Bearer " + service }
    });
    const state = await stateRes.json();
    return reply({ ok: true, state: Array.isArray(state) ? state[0] : null });
  }
  if (body.action !== "publish") return reply({ error: "unknown_action" }, 400);
  const input = body.changes;
  if (!Array.isArray(input) || input.length < 1 || input.length > 250) return reply({ error: "invalid_changes" }, 400);
  const changes: Array<Record<string, unknown>> = [];
  for (const c of input) {
    if (!c || typeof c !== "object") return reply({ error: "invalid_change" }, 400);
    const entity_type = String(c.entity_type || "");
    const entity_id = String(c.entity_id || "").trim();
    const operation = String(c.operation || "");
    if (!["place", "route"].includes(entity_type) || !entity_id || entity_id.length > 180 || !["upsert", "delete"].includes(operation))
      return reply({ error: "invalid_change" }, 400);
    const payload = c.payload ?? null;
    if (operation === "upsert") {
      if (!payload || typeof payload !== "object" || String((payload as Record<string, unknown>).id || "") !== entity_id)
        return reply({ error: "payload_id_mismatch" }, 400);
      if (entity_type === "place") {
        const p = payload as Record<string, unknown>;
        if (!p.name || typeof p.name !== "object" || !Object.values(p.name as Record<string, unknown>).some(v => typeof v === "string" && v.trim()))
          return reply({ error: "place_needs_name" }, 400);
      } else {
        const r = payload as Record<string, unknown>;
        const from = String(r.from || ""), to = String(r.to || "");
        if (!from || !to || from === to || !Array.isArray(r.stops)) return reply({ error: "route_invalid_endpoints_or_stops" }, 400);
        const time = (v: unknown) => typeof v === "string" && /^([01]\d|2[0-3]):[0-5]\d$/.test(v);
        const hasTime = time(r.time) || time(r.arrive) || (r.stops as Array<Record<string, unknown>>).some(s => time(s?.time));
        if (!hasTime) return reply({ error: "route_needs_at_least_one_time" }, 400);
      }
    }
    changes.push({ entity_type, entity_id, operation, payload });
  }
  const stateRes = await fetch(url + "/rest/v1/data_sync_state?select=revision,snapshot_version&id=eq.1", {
    headers: { apikey: service, Authorization: "Bearer " + service }
  });
  if (!stateRes.ok) return reply({ error: "sync_state_read_failed" }, 502);
  const stateRows = await stateRes.json();
  if (!Array.isArray(stateRows) || !stateRows.length) return reply({ error: "sync_state_missing" }, 503);
  const state = stateRows[0], revision = Number(state.revision || 0) + 1;
  const rows = changes.map(c => ({ revision, ...c }));
  const insert = await fetch(url + "/rest/v1/data_change_log", {
    method: "POST",
    headers: { apikey: service, Authorization: "Bearer " + service, "Content-Type": "application/json", Prefer: "resolution=merge-duplicates,return=minimal" },
    body: JSON.stringify(rows)
  });
  if (!insert.ok) return reply({ error: "change_log_write_failed", detail: (await insert.text()).slice(0, 300) }, 502);
  const stateUpdate = await fetch(url + "/rest/v1/data_sync_state?id=eq.1", {
    method: "PATCH",
    headers: { apikey: service, Authorization: "Bearer " + service, "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify({ revision, published_at: new Date().toISOString() })
  });
  if (!stateUpdate.ok) return reply({ error: "sync_state_update_failed", revision }, 502);
  return reply({ ok: true, revision, published: changes.length });
});
