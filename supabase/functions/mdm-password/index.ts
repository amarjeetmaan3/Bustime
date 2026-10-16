import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};
const reply = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: cors });

function b64(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s);
}
function fromB64(s: string): Uint8Array {
  return Uint8Array.from(atob(s), c => c.charCodeAt(0));
}
async function derive(password: string, salt: Uint8Array, iterations: number): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  return new Uint8Array(await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations }, key, 256));
}
function equal(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({ error: "method_not_allowed" }, 405);
  const url = Deno.env.get("SUPABASE_URL");
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !service) return reply({ error: "service_unavailable" }, 503);

  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return reply({ error: "invalid_json" }, 400); }
  const action = String(body.action || "");
  const apiKey = req.headers.get("apikey") || "";
  const auth = req.headers.get("authorization") || "";

  if (action === "set") {
    const token = auth.replace(/^Bearer\s+/i, "");
    if (!token || !apiKey) return reply({ error: "admin_sign_in_required" }, 401);
    const userRes = await fetch(url + "/auth/v1/user", { headers: { apikey: apiKey, Authorization: "Bearer " + token } });
    if (!userRes.ok) return reply({ error: "admin_sign_in_required" }, 401);
    const password = String(body.password || "");
    if (password.length < 8 || password.length > 256) return reply({ error: "password_must_be_8_to_256_characters" }, 400);
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iterations = 310000;
    const hash = await derive(password, salt, iterations);
    const saved = await fetch(url + "/rest/v1/mdm_secret?on_conflict=id", {
      method: "POST",
      headers: { apikey: service, Authorization: "Bearer " + service, "Content-Type": "application/json", Prefer: "resolution=merge-duplicates,return=minimal" },
      body: JSON.stringify({ id: 1, password_salt: b64(salt), password_hash: b64(hash), iterations, updated_at: new Date().toISOString() }),
    });
    if (!saved.ok) return reply({ error: "password_save_failed" }, 500);
    return reply({ ok: true });
  }

  if (action === "verify" || action === "status") {
    const res = await fetch(url + "/rest/v1/mdm_secret?select=password_salt,password_hash,iterations&id=eq.1&limit=1", {
      headers: { apikey: service, Authorization: "Bearer " + service, Accept: "application/json", "Cache-Control": "no-store" },
    });
    if (!res.ok) return reply({ error: "password_check_failed" }, 502);
    const rows = await res.json();
    if (!Array.isArray(rows) || rows.length === 0) return reply({ configured: false, valid: false });
    if (action === "status") return reply({ configured: true });
    const password = String(body.password || "");
    if (!password || password.length > 256) return reply({ configured: true, valid: false }, 401);
    const row = rows[0];
    const actual = await derive(password, fromB64(row.password_salt), Number(row.iterations));
    const valid = equal(actual, fromB64(row.password_hash));
    return valid ? reply({ configured: true, valid: true }) : reply({ configured: true, valid: false }, 401);
  }
  return reply({ error: "unknown_action" }, 400);
});
