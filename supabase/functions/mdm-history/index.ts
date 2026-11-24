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

  const h = { apikey: service, Authorization: "Bearer " + service, "Cache-Control": "no-store" };

  // Device sync diagnostics are visible only to the authenticated owner.
  if (body.action === "sync-monitor") {
    const status = String(body.status || "pending");
    if (!["pending", "resolved", "superseded", "all"].includes(status)) return reply({ error: "invalid_sync_status" }, 400);
    const deviceId = body.device_id === undefined ? "" : String(body.device_id);
    if (deviceId && !/^[0-9a-f-]{36}$/i.test(deviceId)) return reply({ error: "invalid_device_id" }, 400);
    const deviceQuery = url + "/rest/v1/sync_device_state?select=device_id,app_version,last_success_revision,first_seen_at,last_seen_at,last_error_at,last_error" +
      (deviceId ? "&device_id=eq." + encodeURIComponent(deviceId) : "") +
      "&order=last_seen_at.desc&limit=200";
    const errorQuery = url + "/rest/v1/sync_error_logs?select=device_id,revision,entity_type,entity_id,error_code,error_message,attempts,status,first_failed_at,last_failed_at,next_retry_at,resolved_at" +
      (status === "all" ? "" : "&status=eq." + status) +
      (deviceId ? "&device_id=eq." + encodeURIComponent(deviceId) : "") +
      "&order=last_failed_at.desc&limit=500";
    const [deviceRes, errorRes, stateRes] = await Promise.all([
      fetch(deviceQuery, { headers: h }),
      fetch(errorQuery, { headers: h }),
      fetch(url + "/rest/v1/data_sync_state?select=revision,snapshot_version,published_at&id=eq.1", { headers: h })
    ]);
    if (!deviceRes.ok || !errorRes.ok) return reply({ error: "sync_monitor_read_failed" }, 502);
    return reply({
      ok: true,
      devices: await deviceRes.json(),
      errors: await errorRes.json(),
      state: stateRes.ok ? ((await stateRes.json())[0] ?? null) : null
    });
  }

  if (body.action === "sync-retry") {
    const deviceId = String(body.device_id || "");
    const revision = Number(body.revision);
    const entityType = String(body.entity_type || "");
    const entityId = String(body.entity_id || "").trim();
    if (!/^[0-9a-f-]{36}$/i.test(deviceId) || !Number.isSafeInteger(revision) || revision < 1 ||
        !["place", "route"].includes(entityType) || !entityId || entityId.length > 180) {
      return reply({ error: "invalid_sync_retry_target" }, 400);
    }
    const retryUrl = url + "/rest/v1/sync_error_logs?device_id=eq." + encodeURIComponent(deviceId) +
      "&revision=eq." + revision + "&entity_type=eq." + entityType +
      "&entity_id=eq." + encodeURIComponent(entityId) + "&status=eq.pending";
    const retryRes = await fetch(retryUrl, {
      method: "PATCH",
      headers: { ...h, "Content-Type": "application/json", Prefer: "return=representation" },
      body: JSON.stringify({ next_retry_at: new Date().toISOString() })
    });
    if (!retryRes.ok) return reply({ error: "sync_retry_schedule_failed" }, 502);
    const rows = await retryRes.json();
    return rows.length ? reply({ ok: true, scheduled: true }) : reply({ error: "pending_sync_error_not_found" }, 404);
  }

  const limit = Math.min(Math.max(Number(body.limit) || 200, 1), 500);
  const beforeRaw = body.before_revision === undefined ? null : Number(body.before_revision);
  const beforeType = String(body.before_entity_type || "");
  const beforeId = String(body.before_entity_id || "");
  if (beforeRaw !== null && (!Number.isSafeInteger(beforeRaw) || beforeRaw < 1)) return reply({ error: "invalid_before_revision" }, 400);
  if (beforeRaw !== null && ((beforeType && !["place", "route"].includes(beforeType)) || (beforeId && !/^[A-Za-z0-9_-]{1,180}$/.test(beforeId)))) return reply({ error: "invalid_history_cursor" }, 400);

  const filters: string[] = [];
  const operation = String(body.filter_operation || "");
  const entityType = String(body.filter_entity_type || "");
  const source = String(body.filter_source || "");
  const search = String(body.filter_search || "").trim().slice(0, 100);
  const dateFrom = String(body.filter_from || "");
  const dateTo = String(body.filter_to || "");
  if (operation && !["upsert", "delete"].includes(operation)) return reply({ error: "invalid_history_operation_filter" }, 400);
  if (entityType && !["place", "route"].includes(entityType)) return reply({ error: "invalid_history_type_filter" }, 400);
  if (source && !["web_admin", "android_app", "legacy"].includes(source)) return reply({ error: "invalid_history_source_filter" }, 400);
  if (operation) filters.push("operation=eq." + operation);
  if (entityType) filters.push("entity_type=eq." + entityType);
  if (source) filters.push("source=eq." + source);
  if (search) filters.push("entity_id=ilike.*" + encodeURIComponent(search) + "*");
  if (dateFrom && !Number.isNaN(Date.parse(dateFrom))) filters.push("created_at=gte." + encodeURIComponent(new Date(dateFrom).toISOString()));
  if (dateTo && !Number.isNaN(Date.parse(dateTo))) filters.push("created_at=lt." + encodeURIComponent(new Date(dateTo + "T23:59:59.999Z").toISOString()));

  let cursorFilter = "";
  if (beforeRaw !== null) {
    if (beforeType && beforeId) {
      cursorFilter = "&or=(revision.lt." + beforeRaw +
        ",and(revision.eq." + beforeRaw + ",entity_type.gt." + beforeType + ")" +
        ",and(revision.eq." + beforeRaw + ",entity_type.eq." + beforeType + ",entity_id.gt." + beforeId + "))";
    } else {
      cursorFilter = "&revision=lt." + beforeRaw;
    }
  }
  const logUrl = url + "/rest/v1/data_change_log?select=revision,source,entity_type,entity_id,operation,payload,created_at" +
    cursorFilter + (filters.length ? "&" + filters.join("&") : "") +
    "&order=revision.desc,entity_type.asc,entity_id.asc&limit=" + limit;
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
