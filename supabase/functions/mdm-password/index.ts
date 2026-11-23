import "jsr:@supabase/functions-js/edge-runtime.d.ts";

// Retired: the shared MDM password was replaced by the owner's Supabase sign-in.
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, authorization, content-type, cache-control",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};
Deno.serve((req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  return new Response(JSON.stringify({ error: "retired" }), { status: 410, headers: cors });
});
