import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, authorization, content-type, cache-control",
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
  let authorized = false;
  const bearer = req.headers.get("Authorization") || "";
  if (!password && /^Bearer\s+/.test(bearer)) {
    const userRes = await fetch(url + "/auth/v1/user", {
      headers: { apikey: req.headers.get("apikey") || "", Authorization: bearer, "Cache-Control": "no-store" }
    });
    if (userRes.ok) {
      const user = await userRes.json();
      authorized = !!user && user.id === "b0787029-45dc-4f37-8568-6a09a4fe2ad9";
    }
  }
  if (!authorized) {
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
  }

  // Shared Recycle Bin actions; only the authenticated owner/MDM password reaches here.
  if (body.action === "recycle-list") {
    const r = await fetch(url + "/rest/v1/data_recycle_bin?select=id,entity_type,entity_id,payload,deleted_at,deleted_revision&restored_at=is.null&permanently_deleted_at=is.null&order=deleted_at.desc&limit=500", {
      headers: { apikey: service, Authorization: "Bearer " + service, "Cache-Control": "no-store" }
    });
    if (!r.ok) return reply({ error: "recycle_bin_read_failed" }, 502);
    return reply({ ok: true, items: await r.json() });
  }
  if (body.action === "recycle-purge") {
    const id = Number(body.id);
    if (!Number.isSafeInteger(id) || id < 1) return reply({ error: "invalid_recycle_id" }, 400);
    const r = await fetch(url + "/rest/v1/data_recycle_bin?id=eq." + id + "&restored_at=is.null&permanently_deleted_at=is.null", {
      method: "PATCH",
      headers: { apikey: service, Authorization: "Bearer " + service, "Content-Type": "application/json", Prefer: "return=representation" },
      body: JSON.stringify({ permanently_deleted_at: new Date().toISOString(), payload: null })
    });
    if (!r.ok) return reply({ error: "recycle_purge_failed" }, 502);
    const rows = await r.json();
    return rows.length ? reply({ ok: true, purged: true }) : reply({ error: "recycle_item_not_found" }, 404);
  }
  let restoreId: number | null = null;
  if (body.action === "recycle-restore") {
    const id = Number(body.id);
    if (!Number.isSafeInteger(id) || id < 1) return reply({ error: "invalid_recycle_id" }, 400);
    const r = await fetch(url + "/rest/v1/data_recycle_bin?select=id,entity_type,entity_id,payload&id=eq." + id + "&restored_at=is.null&permanently_deleted_at=is.null&limit=1", {
      headers: { apikey: service, Authorization: "Bearer " + service, "Cache-Control": "no-store" }
    });
    if (!r.ok) return reply({ error: "recycle_item_read_failed" }, 502);
    const rows = await r.json();
    if (!Array.isArray(rows) || !rows.length || !rows[0].payload) return reply({ error: "recycle_item_not_found_or_payload_missing" }, 404);
    const item = rows[0];
    restoreId = id;
    body.action = "publish";
    body.changes = [{ entity_type: item.entity_type, entity_id: item.entity_id, operation: "upsert", payload: item.payload }];
  }

  if (body.action === "list") {
    const stateRes = await fetch(url + "/rest/v1/data_sync_state?select=revision,snapshot_version,published_at&id=eq.1", {
      headers: { apikey: service, Authorization: "Bearer " + service }
    });
    const state = await stateRes.json();
    return reply({ ok: true, state: Array.isArray(state) ? state[0] : null });
  }
  if (body.action === "snapshot") {
    const snapshotVersion = Number(body.snapshot_version);
    if (!Number.isSafeInteger(snapshotVersion) || snapshotVersion < 1) return reply({ error: "invalid_snapshot_version" }, 400);
    const snapshotUpdate = await fetch(url + "/rest/v1/data_sync_state?id=eq.1", {
      method: "PATCH",
      headers: { apikey: service, Authorization: "Bearer " + service, "Content-Type": "application/json", Prefer: "return=minimal" },
      body: JSON.stringify({ snapshot_version: snapshotVersion, published_at: new Date().toISOString() })
    });
    if (!snapshotUpdate.ok) return reply({ error: "snapshot_state_update_failed" }, 502);
    return reply({ ok: true, snapshot_version: snapshotVersion });
  }
  if (body.action !== "publish") return reply({ error: "unknown_action" }, 400);
  const snapshotVersion = body.snapshot_version === undefined ? null : Number(body.snapshot_version);
  if (snapshotVersion !== null && (!Number.isSafeInteger(snapshotVersion) || snapshotVersion < 1)) return reply({ error: "invalid_snapshot_version" }, 400);
  const input = body.changes;
  if (!Array.isArray(input) || input.length < 1 || input.length > 250) return reply({ error: "invalid_changes" }, 400);
  const changes: Array<Record<string, unknown>> = [];
  const deleteArchives: Array<{entity_type:string;entity_id:string;payload:unknown}> = [];
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
    if (operation === "delete") {
      let savedPayload: unknown = payload && typeof payload === "object" ? payload : null;
      if (!savedPayload) {
        const prior = await fetch(url + "/rest/v1/data_change_log?select=payload&entity_type=eq." + entity_type + "&entity_id=eq." + encodeURIComponent(entity_id) + "&operation=eq.upsert&order=revision.desc&limit=1", {
          headers: { apikey: service, Authorization: "Bearer " + service, "Cache-Control": "no-store" }
        });
        if (prior.ok) {
          const priorRows = await prior.json();
          if (Array.isArray(priorRows) && priorRows.length) savedPayload = priorRows[0].payload;
        }
      }
      if (!savedPayload || typeof savedPayload !== "object") return reply({ error: "delete_payload_required_for_recovery", entity_type, entity_id }, 409);
      deleteArchives.push({ entity_type, entity_id, payload: savedPayload });
    }
    changes.push({ entity_type, entity_id, operation, payload: operation === "delete" ? null : payload });
  }
  const stateRes = await fetch(url + "/rest/v1/data_sync_state?select=revision,snapshot_version&id=eq.1", {
    headers: { apikey: service, Authorization: "Bearer " + service }
  });
  if (!stateRes.ok) return reply({ error: "sync_state_read_failed" }, 502);
  const stateRows = await stateRes.json();
  if (!Array.isArray(stateRows) || !stateRows.length) return reply({ error: "sync_state_missing" }, 503);
  const state = stateRows[0], revision = Number(state.revision || 0) + 1;
  // Archive complete payloads before emitting tombstones, so a failed archive never destroys recoverability.
  for (const item of deleteArchives) {
    const existing = await fetch(url + "/rest/v1/data_recycle_bin?select=id&entity_type=eq." + item.entity_type + "&entity_id=eq." + encodeURIComponent(item.entity_id) + "&restored_at=is.null&permanently_deleted_at=is.null&limit=1", {
      headers: { apikey: service, Authorization: "Bearer " + service }
    });
    if (!existing.ok) return reply({ error: "recycle_bin_check_failed" }, 502);
    const existingRows = await existing.json();
    let archived: Response;
    if (Array.isArray(existingRows) && existingRows.length) {
      archived = await fetch(url + "/rest/v1/data_recycle_bin?id=eq." + existingRows[0].id, {
        method: "PATCH",
        headers: { apikey: service, Authorization: "Bearer " + service, "Content-Type": "application/json", Prefer: "return=minimal" },
        body: JSON.stringify({ payload: item.payload, deleted_at: new Date().toISOString() })
      });
    } else {
      archived = await fetch(url + "/rest/v1/data_recycle_bin", {
        method: "POST",
        headers: { apikey: service, Authorization: "Bearer " + service, "Content-Type": "application/json", Prefer: "return=minimal" },
        body: JSON.stringify({ entity_type: item.entity_type, entity_id: item.entity_id, payload: item.payload, deleted_revision: revision })
      });
    }
    if (!archived.ok) return reply({ error: "recycle_bin_archive_failed", detail: (await archived.text()).slice(0, 200) }, 502);
  }
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
    body: JSON.stringify(Object.assign({ revision, published_at: new Date().toISOString() }, snapshotVersion === null ? {} : { snapshot_version: snapshotVersion }))
  });
  if (!stateUpdate.ok) return reply({ error: "sync_state_update_failed", revision }, 502);
  if (restoreId !== null) {
    const restored = await fetch(url + "/rest/v1/data_recycle_bin?id=eq." + restoreId + "&restored_at=is.null", {
      method: "PATCH",
      headers: { apikey: service, Authorization: "Bearer " + service, "Content-Type": "application/json", Prefer: "return=minimal" },
      body: JSON.stringify({ restored_at: new Date().toISOString() })
    });
    if (!restored.ok) return reply({ error: "restore_marker_failed", revision }, 502);
  }
  return reply({ ok: true, revision, published: changes.length, restored: restoreId !== null });
});
