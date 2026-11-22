import "jsr:@supabase/functions-js/edge-runtime.d.ts";

// Read-only history of published data changes. Owner (Supabase Auth Bearer token) only.
const OWNER_ID = "b0787029-45dc-4f37-8568-6a09a4fe2ad9";
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, authorization, content-type, cache-control",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: cors });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({ error: "method_not_allowed" }, 405);
  const url = Deno.env.get("SUPABASE_URL"), service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !service) return reply({ error: "service_unavailable" }, 503);
  let body: Record<string, unknown> = {};
  try { body = await req.json(); } catch { /* empty body is fine */ }

  const bearer = req.headers.get("Authorization") || "";
  if (!/^Bearer\s+/.test(bearer)) return reply({ error: "admin_sign_in_required" }, 401);
  const userRes = await fetch(url + "/auth/v1/user", {
    headers: { apikey: req.headers.get("apikey") || "", Authorization: bearer, "Cache-Control": "no-store" }
  });
  if (!userRes.ok) return reply({ error: "admin_sign_in_required" }, 401);
  const user = await userRes.json();
  if (!user || user.id !== OWNER_ID) return reply({ error: "not_allowed" }, 403);

  const limit = Math.min(Math.max(Number(body.limit) || 200, 1), 500);
  const beforeRaw = body.before_revision === undefined ? null : Number(body.before_revision);
  if (beforeRaw !== null && (!Number.isSafeInteger(beforeRaw) || beforeRaw < 1)) return reply({ error: "invalid_before_revision" }, 400);
  const h = { apikey: service, Authorization: "Bearer " + service, "Cache-Control": "no-store" };

  const logUrl = url + "/rest/v1/data_change_log?select=revision,entity_type,entity_id,operation,payload,created_at" +
    (beforeRaw !== null ? "&revision=lt." + beforeRaw : "") + "&order=revision.desc,entity_type.asc,entity_id.asc&limit=" + limit;
  const [logRes, stateRes, binRes] = await Promise.all([
    fetch(logUrl, { headers: h }),
    fetch(url + "/rest/v1/data_sync_state?select=revision,snapshot_version,published_at&id=eq.1", { headers: h }),
    fetch(url + "/rest/v1/data_recycle_bin?select=entity_type,entity_id,deleted_revision,restored_at,permanently_deleted_at&order=deleted_at.desc&limit=500", { headers: h }),
  ]);
  if (!logRes.ok) return reply({ error: "history_read_failed" }, 502);
  const rows = await logRes.json() as Array<Record<string, unknown>>;
  const state = stateRes.ok ? (await stateRes.json())[0] ?? null : null;
  const bin = binRes.ok ? await binRes.json() : [];

  // "before" = the previous upsert payload of the same record, when it is inside this page.
  const items = rows.map((row, i) => {
    let before: unknown = null;
    for (let j = i + 1; j < rows.length; j++) {
      const o = rows[j];
      if (o.entity_type === row.entity_type && o.entity_id === row.entity_id && o.operation === "upsert") { before = o.payload; break; }
    }
    return { ...row, before };
  });
  return reply({ ok: true, state, items, recycle: bin, has_more: rows.length === limit });
});
