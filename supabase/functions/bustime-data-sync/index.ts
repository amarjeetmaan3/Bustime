import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, authorization, content-type",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return new Response(JSON.stringify({ error: "method_not_allowed" }), { status: 405, headers: corsHeaders });

  const url = new URL(req.url);
  const action = url.searchParams.get("action") ?? "status";
  const base = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !serviceKey) return new Response(JSON.stringify({ error: "sync_service_unavailable" }), { status: 503, headers: corsHeaders });

  let path: string;
  if (action === "status") {
    path = "/rest/v1/data_sync_state?select=revision,snapshot_version&id=eq.1";
  } else if (action === "changes") {
    const sinceRaw = url.searchParams.get("since") ?? "0";
    const offsetRaw = url.searchParams.get("offset") ?? "0";
    if (!/^\d+$/.test(sinceRaw) || !/^\d+$/.test(offsetRaw)) return new Response(JSON.stringify({ error: "invalid_cursor" }), { status: 400, headers: corsHeaders });
    const since = Number(sinceRaw), offset = Number(offsetRaw);
    if (!Number.isSafeInteger(since) || !Number.isSafeInteger(offset) || offset > 20000) return new Response(JSON.stringify({ error: "cursor_out_of_range" }), { status: 400, headers: corsHeaders });
    path = `/rest/v1/data_change_log?select=revision,entity_type,entity_id,operation,payload&revision=gt.${since}&order=revision.asc,entity_type.asc,entity_id.asc&limit=1000&offset=${offset}`;
  } else return new Response(JSON.stringify({ error: "unknown_action" }), { status: 400, headers: corsHeaders });

  try {
    const upstream = await fetch(base.replace(/\/$/, "") + path, {
      headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, Accept: "application/json", "Cache-Control": "no-store" },
    });
    return new Response(await upstream.text(), { status: upstream.status, headers: { ...corsHeaders, "Cache-Control": "no-store" } });
  } catch (_error) {
    return new Response(JSON.stringify({ error: "sync_upstream_failed" }), { status: 502, headers: corsHeaders });
  }
});
