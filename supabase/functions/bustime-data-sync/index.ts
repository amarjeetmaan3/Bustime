import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, authorization, content-type, cache-control, accept",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

const reply = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: corsHeaders });

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET" && req.method !== "POST") return reply({ error: "method_not_allowed" }, 405);

  const base = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !serviceKey) return reply({ error: "sync_service_unavailable" }, 503);
  const root = base.replace(/\/$/, "");
  const serviceHeaders = {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    Accept: "application/json",
    "Content-Type": "application/json",
    "Cache-Control": "no-store",
  };

  if (req.method === "POST") {
    let body: Record<string, unknown>;
    try {
      const raw = await req.text();
      if (raw.length > 180_000) return reply({ error: "report_too_large" }, 413);
      body = JSON.parse(raw);
    } catch {
      return reply({ error: "invalid_json" }, 400);
    }
    if (body.action !== "report") return reply({ error: "unknown_action" }, 400);
    const deviceId = String(body.device_id || "");
    const revision = Number(body.revision);
    const appVersion = String(body.app_version || "").slice(0, 40);
    const failures = Array.isArray(body.failures) ? body.failures : [];
    const resolved = Array.isArray(body.resolved) ? body.resolved : [];
    if (!uuidPattern.test(deviceId) || !Number.isSafeInteger(revision) || revision < 0 ||
        failures.length > 500 || resolved.length > 500) {
      return reply({ error: "invalid_sync_report" }, 400);
    }
    const cleanFailures = [];
    for (const item of failures) {
      if (!item || typeof item !== "object") return reply({ error: "invalid_failure_item" }, 400);
      const row = item as Record<string, unknown>;
      const r = Number(row.revision);
      const type = String(row.entity_type || "");
      const id = String(row.entity_id || "").trim();
      if (!Number.isSafeInteger(r) || r < 1 || r > revision ||
          !["place", "route"].includes(type) || !id || id.length > 180) {
        return reply({ error: "invalid_failure_item" }, 400);
      }
      cleanFailures.push({
        revision: r,
        entity_type: type,
        entity_id: id,
        error_code: String(row.error_code || "apply_failed").slice(0, 80),
        error_message: String(row.error_message || "Change could not be applied").slice(0, 300),
      });
    }
    const cleanResolved = [];
    for (const item of resolved) {
      if (!item || typeof item !== "object") return reply({ error: "invalid_resolved_item" }, 400);
      const row = item as Record<string, unknown>;
      const r = Number(row.revision);
      const type = String(row.entity_type || "");
      const id = String(row.entity_id || "").trim();
      if (!Number.isSafeInteger(r) || r < 1 || r > revision ||
          !["place", "route"].includes(type) || !id || id.length > 180) {
        return reply({ error: "invalid_resolved_item" }, 400);
      }
      cleanResolved.push({ revision: r, entity_type: type, entity_id: id });
    }

    try {
      const result = await fetch(root + "/rest/v1/rpc/record_bustime_sync_report", {
        method: "POST",
        headers: serviceHeaders,
        body: JSON.stringify({
          p_device_id: deviceId,
          p_app_version: appVersion,
          p_revision: revision,
          p_failures: cleanFailures,
          p_resolved: cleanResolved,
          p_snapshot_recovered: body.snapshot_recovered === true,
        }),
      });
      const text = await result.text();
      return new Response(text || JSON.stringify({ error: "empty_sync_report_response" }), {
        status: result.status,
        headers: corsHeaders,
      });
    } catch {
      return reply({ error: "sync_report_unavailable" }, 502);
    }
  }

  const url = new URL(req.url);
  const action = url.searchParams.get("action") ?? "status";

  if (action === "snapshot") {
    try {
      const response = await fetch(root + "/rest/v1/rpc/get_bustime_snapshot", {
        method: "POST",
        headers: serviceHeaders,
        body: "{}",
      });
      if (!response.ok) return reply({ error: "master_snapshot_read_failed" }, 502);
      const snapshot = await response.json();
      if (!snapshot || typeof snapshot !== "object" ||
          !Number(snapshot.dataVersion) || !Number(snapshot.snapshotRevision) ||
          !Array.isArray(snapshot.places) || !snapshot.places.length ||
          !Array.isArray(snapshot.routes) || !snapshot.routes.length) {
        return reply({ error: "master_snapshot_invalid" }, 503);
      }
      return reply(snapshot);
    } catch {
      return reply({ error: "master_snapshot_unavailable" }, 502);
    }
  }

  if (action === "status") {
    const path = "/rest/v1/data_sync_state?select=revision,snapshot_version,published_at&id=eq.1";
    try {
      const response = await fetch(root + path, { headers: serviceHeaders });
      return new Response(await response.text(), { status: response.status, headers: corsHeaders });
    } catch {
      return reply({ error: "sync_status_unavailable" }, 502);
    }
  }

  if (action === "changes") {
    const sinceRaw = url.searchParams.get("since") ?? "0";
    const offsetRaw = url.searchParams.get("offset") ?? "0";
    if (!/^\d+$/.test(sinceRaw) || !/^\d+$/.test(offsetRaw)) return reply({ error: "invalid_cursor" }, 400);
    const since = Number(sinceRaw), offset = Number(offsetRaw);
    if (!Number.isSafeInteger(since) || !Number.isSafeInteger(offset) || offset < 0) {
      return reply({ error: "cursor_out_of_range" }, 400);
    }
    if (offset >= 20000) return reply({ error: "snapshot_required", reason: "change_history_page_limit" }, 409);
    const path = `/rest/v1/data_change_log?select=revision,entity_type,entity_id,operation,payload&revision=gt.${since}&order=revision.asc,entity_type.asc,entity_id.asc&limit=1000&offset=${offset}`;
    try {
      const response = await fetch(root + path, { headers: serviceHeaders });
      return new Response(await response.text(), { status: response.status, headers: corsHeaders });
    } catch {
      return reply({ error: "sync_upstream_failed" }, 502);
    }
  }

  if (action === "retry") {
    const deviceId = url.searchParams.get("device_id") ?? "";
    if (!uuidPattern.test(deviceId)) return reply({ error: "invalid_device_id" }, 400);
    try {
      const response = await fetch(root + "/rest/v1/rpc/get_bustime_sync_retries", {
        method: "POST",
        headers: serviceHeaders,
        body: JSON.stringify({ p_device_id: deviceId, p_limit: 100 }),
      });
      return new Response(await response.text(), { status: response.status, headers: corsHeaders });
    } catch {
      return reply({ error: "sync_retry_unavailable" }, 502);
    }
  }

  return reply({ error: "unknown_action" }, 400);
});
