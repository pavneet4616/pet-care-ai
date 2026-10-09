import type { Env } from "../types.js";
import { accountHash } from "./crypto.js";

/** One strongly consistent registry for token ownership and centrally reviewable safety reports. */
export class RegistryDO {
  private sql: SqlStorage;
  constructor(state: DurableObjectState) {
    this.sql = state.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS owners(token_hash TEXT NOT NULL, installation_id TEXT NOT NULL, kind TEXT NOT NULL, last_seen INTEGER NOT NULL, PRIMARY KEY(token_hash,installation_id));
      CREATE TABLE IF NOT EXISTS receipts(token_hash TEXT PRIMARY KEY, kind TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS reports(id TEXT PRIMARY KEY, installation_id TEXT NOT NULL, created_at INTEGER NOT NULL, category TEXT NOT NULL, request_id TEXT, detail TEXT, excerpt TEXT, reviewed_at INTEGER);
      CREATE TABLE IF NOT EXISTS limits(key TEXT PRIMARY KEY, day TEXT NOT NULL, used INTEGER NOT NULL);
    `);
  }
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/reports" && request.method === "GET") {
      const before = Number(url.searchParams.get("before") ?? Date.now() + 1);
      return Response.json({ reports: this.sql.exec("SELECT id,created_at,category,request_id,detail,excerpt,reviewed_at FROM reports WHERE created_at < ? ORDER BY created_at DESC LIMIT 100", before).toArray() });
    }
    const body = await request.json() as Record<string, string | number>;
    if (url.pathname === "/bind-token") {
      const hash = String(body.tokenHash), installation = String(body.installationId), kind = String(body.kind);
      const owners = this.sql.exec("SELECT installation_id FROM owners WHERE token_hash=?",hash).toArray();
      if (!owners.some(row => row.installation_id === installation)) {
        // New Play purchases must match the purchasing installation. Already-bound receipts can
        // restore onto at most three attested installations, including reinstalls/phone transfers.
        const knownReceipt = this.sql.exec("SELECT token_hash FROM receipts WHERE token_hash=?",hash).toArray().length > 0;
        if (!owners.length && !knownReceipt && body.accountId && body.accountId !== await accountHash(installation)) {
          return Response.json({ error: "PURCHASE_ACCOUNT_MISMATCH" },{status:403});
        }
        // Re-read after hashing (an await may interleave another request).
        const current = this.sql.exec("SELECT installation_id FROM owners WHERE token_hash=?",hash).toArray();
        if (current.length >= 3 && !current.some(row => row.installation_id === installation)) return Response.json({error:"DEVICE_LIMIT_REACHED"},{status:409});
      }
      // Keep an anonymous receipt marker after deletion so phone transfers remain restorable.
      this.sql.exec("INSERT OR IGNORE INTO receipts VALUES(?,?)",hash,kind);
      this.sql.exec("INSERT INTO owners VALUES(?,?,?,?) ON CONFLICT(token_hash,installation_id) DO UPDATE SET last_seen=excluded.last_seen",hash,installation,kind,Date.now());
      return Response.json({ok:true});
    }
    if (url.pathname === "/owners") return Response.json({ installations: this.sql.exec("SELECT installation_id,kind FROM owners WHERE token_hash=?", String(body.tokenHash)).toArray() });
    if (url.pathname === "/release-device") {
      this.sql.exec("DELETE FROM owners WHERE token_hash=? AND installation_id=?",String(body.tokenHash),String(body.installationId));
      return Response.json({ok:true});
    }
    if (url.pathname === "/report") {
      this.sql.exec("DELETE FROM reports WHERE created_at < ?",Date.now()-90*86_400_000);
      this.sql.exec("INSERT OR IGNORE INTO reports(id,installation_id,created_at,category,request_id,detail,excerpt) VALUES(?,?,?,?,?,?,?)",String(body.id),String(body.installationId),Date.now(),String(body.category),String(body.requestId ?? ""),String(body.detail ?? ""),String(body.excerpt ?? ""));
      return Response.json({ok:true});
    }
    if (url.pathname === "/review") {
      this.sql.exec("UPDATE reports SET reviewed_at=? WHERE id=?",Date.now(),String(body.id));
      return Response.json({ok:true});
    }
    if (url.pathname === "/delete") {
      this.sql.exec("DELETE FROM reports WHERE installation_id=?",String(body.installationId));
      this.sql.exec("DELETE FROM owners WHERE installation_id=?",String(body.installationId));
      return Response.json({ok:true});
    }
    if (url.pathname === "/cost-guard") {
      const key = String(body.key), day = new Date().toISOString().slice(0,10);
      this.sql.exec("DELETE FROM limits WHERE day < ?",day);
      const row=this.sql.exec("SELECT used FROM limits WHERE key=? AND day=?",key,day).toArray()[0];
      if (Number(row?.used ?? 0) >= Number(body.limit)) return Response.json({allowed:false});
      this.sql.exec("INSERT INTO limits VALUES(?,?,1) ON CONFLICT(key) DO UPDATE SET day=excluded.day,used=CASE WHEN limits.day=excluded.day THEN limits.used+1 ELSE 1 END",key,day);
      return Response.json({allowed:true});
    }
    return Response.json({error:"not found"},{status:404});
  }
}

export async function registry(env: Env, path: string, body?: object): Promise<Response> {
  const response = await env.SECURITY_REGISTRY.get(env.SECURITY_REGISTRY.idFromName("audit-registry-v1")).fetch(`https://registry${path}`,{
    method:body === undefined ? "GET" : "POST", ...(body === undefined ? {} : {body:JSON.stringify(body)}), headers:{"content-type":"application/json"},
  });
  return response;
}
