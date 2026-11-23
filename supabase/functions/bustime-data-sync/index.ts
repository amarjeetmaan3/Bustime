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
  if (action === "snapshot") {
    try {
      const [stateRes, placesRes, routesRes] = await Promise.all([
        fetch(base.replace(/\/$/, "") + "/rest/v1/data_sync_state?select=revision,snapshot_version&id=eq.1", {
          headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Cache-Control": "no-store" },
        }),
        fetch(base.replace(/\/$/, "") + "/rest/v1/master_places?select=payload&order=id.asc", {
          headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Cache-Control": "no-store" },
        }),
        fetch(base.replace(/\/$/, "") + "/rest/v1/master_routes?select=payload&order=id.asc", {
          headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Cache-Control": "no-store" },
        }),
      ]);
      if (!stateRes.ok || !placesRes.ok || !routesRes.ok) {
        return new Response(JSON.stringify({ error: "master_snapshot_read_failed" }), { status: 502, headers: corsHeaders });
      }
      const [states, placeRows, routeRows] = await Promise.all([stateRes.json(), placesRes.json(), routesRes.json()]);
      if (!Array.isArray(states) || !states.length || !Array.isArray(placeRows) || !Array.isArray(routeRows)) {
        return new Response(JSON.stringify({ error: "master_snapshot_invalid" }), { status: 503, headers: corsHeaders });
      }
      const snapshot = {
        dataVersion: Number(states[0].snapshot_version || 0),
        snapshotRevision: Number(states[0].revision || 0),
        places: placeRows.map((row: { payload: unknown }) => row.payload),
        routes: routeRows.map((row: { payload: unknown }) => row.payload),
      };
      if (!snapshot.dataVersion || !snapshot.snapshotRevision || !snapshot.places.length || !snapshot.routes.length) {
        return new Response(JSON.stringify({ error: "master_snapshot_empty" }), { status: 503, headers: corsHeaders });
      }
      return new Response(JSON.stringify(snapshot), { headers: { ...corsHeaders, "Cache-Control": "no-store" } });
    } catch (_error) {
      return new Response(JSON.stringify({ error: "master_snapshot_unavailable" }), { status: 502, headers: corsHeaders });
    }
  } else if (action === "status") {
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
