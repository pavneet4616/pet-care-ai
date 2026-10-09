import { carryTier } from "../billing/entitlements.js";
import type { QuotaSnapshot, ReserveResult, Tier } from "../types.js";

// SQLite-backed Durable Object: one instance per installationId.
// Strongly consistent quota accounting (KV is NOT suitable for counters).

interface QuotaRow {
  [key: string]: SqlStorageValue;
  day: string;
  used: number;
  bonusUsed: number;
  tier: Tier;
  proExpiry: number;
}

function todayDay(): string {
  return new Date().toISOString().slice(0, 10);
}

function nextMidnightUtc(): string {
  const now = new Date();
  const next = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1),
  );
  return next.toISOString();
}

export class AiQuotaDO {
  private sql: SqlStorage;
  private limits: { free: number; lifetime: number; pro: number; bonus: number };

  constructor(
    private state: DurableObjectState,
    env: Record<string, string | undefined>,
  ) {
    this.sql = state.storage.sql;
    this.limits = {
      free: parseInt(env.FREE_DAILY ?? "5", 10),
      lifetime: parseInt(env.LIFETIME_DAILY ?? "10", 10),
      pro: parseInt(env.PRO_DAILY ?? "20", 10),
      bonus: parseInt(env.BONUS_DAILY ?? "1", 10),
    };
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS quota (
        day TEXT PRIMARY KEY, used INTEGER NOT NULL DEFAULT 0,
        bonus_used INTEGER NOT NULL DEFAULT 0, tier TEXT NOT NULL DEFAULT 'free',
        pro_expiry INTEGER NOT NULL DEFAULT 0
      );
      CREATE TABLE IF NOT EXISTS processed_requests (
        request_id TEXT PRIMARY KEY, day TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'regular'
      );
      CREATE TABLE IF NOT EXISTS reports (
        id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL,
        request_id TEXT, category TEXT NOT NULL, detail TEXT, excerpt TEXT
      );
      CREATE TABLE IF NOT EXISTS auth (id INTEGER PRIMARY KEY CHECK(id=1), secret_hash TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS reward_grants(transaction_id TEXT PRIMARY KEY, day TEXT NOT NULL, used_by TEXT);
      CREATE TABLE IF NOT EXISTS entitlement_source(id INTEGER PRIMARY KEY CHECK(id=1), token_hash TEXT, lifetime INTEGER NOT NULL DEFAULT 0);
    `);
    // Upgrade existing SQLite objects without deleting their counters.
    const columns = this.sql.exec("PRAGMA table_info(processed_requests)").toArray();
    if (!columns.some((column) => column.name === "kind")) {
      this.sql.exec("ALTER TABLE processed_requests ADD COLUMN kind TEXT NOT NULL DEFAULT 'regular'");
    }
  }

  private getRow(day: string): QuotaRow {
    const cutoff = new Date(Date.parse(`${day}T00:00:00Z`) - 2 * 86_400_000).toISOString().slice(0, 10);
    this.sql.exec("DELETE FROM processed_requests WHERE day < ?", cutoff);
    this.sql.exec("DELETE FROM reward_grants WHERE day < ?", cutoff);
    this.sql.exec("DELETE FROM quota WHERE day < ? AND day != (SELECT MAX(day) FROM quota)",cutoff);
    const row = this.sql
      .exec<QuotaRow>("SELECT day, used, bonus_used AS bonusUsed, tier, pro_expiry AS proExpiry FROM quota WHERE day = ?", day)
      .toArray()[0];
    if (row) {
      // Pro expired -> downgrade, keep lifetime flag if set.
      if (row.tier === "pro" && row.proExpiry > 0 && row.proExpiry <= Date.now()) {
        const lifetime=this.sql.exec("SELECT lifetime FROM entitlement_source WHERE id=1").toArray()[0]?.lifetime === 1;
        const tier=lifetime ? "lifetime" : "free";
        this.sql.exec("UPDATE quota SET tier = ?, pro_expiry = 0 WHERE day = ?", tier, day);
        return { ...row, tier, proExpiry: 0 };
      }
      return row;
    }
    // New day: carry tier forward from latest row.
    const prev = this.sql
      .exec<QuotaRow>("SELECT day, used, bonus_used AS bonusUsed, tier, pro_expiry AS proExpiry FROM quota ORDER BY day DESC LIMIT 1")
      .toArray()[0];
    let tier = carryTier(prev);
    if (tier === "free" && this.sql.exec("SELECT lifetime FROM entitlement_source WHERE id=1").toArray()[0]?.lifetime === 1) tier="lifetime";
    this.sql.exec(
      "INSERT INTO quota (day, used, bonus_used, tier, pro_expiry) VALUES (?, 0, 0, ?, ?)",
      day,
      tier,
      prev?.proExpiry ?? 0,
    );
    return { day, used: 0, bonusUsed: 0, tier, proExpiry: prev?.proExpiry ?? 0 };
  }

  private limitFor(tier: Tier): number {
    return tier === "pro" ? this.limits.pro : tier === "lifetime" ? this.limits.lifetime : this.limits.free;
  }

  private snapshot(row: QuotaRow): QuotaSnapshot {
    const limit = this.limitFor(row.tier);
    return {
      tier: row.tier,
      limit,
      used: row.used,
      remaining: Math.max(0, limit - row.used),
      bonusRemaining: Math.max(0, this.limits.bonus - row.bonusUsed),
      bonusReady: this.sql.exec("SELECT 1 FROM reward_grants WHERE day=? AND used_by IS NULL LIMIT 1", row.day).toArray().length > 0 && row.bonusUsed < this.limits.bonus,
      resetAt: nextMidnightUtc(),
    };
  }

  private alreadyProcessed(requestId: string): boolean {
    return (
      this.sql
        .exec("SELECT 1 FROM processed_requests WHERE request_id = ?", requestId)
        .toArray().length > 0
    );
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const day = todayDay();
    this.getRow(day);

    if (url.pathname === "/authenticate" && request.method === "POST") {
      const { secretHash,checkOnly } = await request.json() as { secretHash: string;checkOnly?:boolean };
      const row = this.sql.exec("SELECT secret_hash FROM auth WHERE id=1").toArray()[0];
      if ((checkOnly && !row) || (row && row.secret_hash !== secretHash)) return Response.json({error:"AUTH_FAILED"},{status:403});
      this.sql.exec("INSERT OR IGNORE INTO auth VALUES(1,?)",secretHash);
      return Response.json({ok:true});
    }
    if (url.pathname === "/grant-bonus" && request.method === "POST") {
      const { transactionId }=await request.json() as {transactionId:string};
      this.sql.exec("INSERT OR IGNORE INTO reward_grants VALUES(?,?,NULL)",transactionId,day);
      return Response.json({ok:true});
    }
    if (url.pathname === "/delete-data" && request.method === "POST") {
      this.sql.exec("DELETE FROM reports");
      this.sql.exec("DELETE FROM entitlement_source");
      this.sql.exec("UPDATE quota SET tier='free',pro_expiry=0");
      // Anonymous counters, reward transaction IDs, and auth proof remain only for anti-abuse.
      return Response.json({ok:true});
    }

    if (url.pathname === "/reserve" && request.method === "POST") {
      const { requestId } = (await request.json()) as { requestId: string };
      if (this.alreadyProcessed(requestId)) {
        const quota = this.snapshot(this.getRow(day));
        return Response.json({ duplicate: true, allowed: true, quota } satisfies ReserveResult);
      }
      const row = this.getRow(day);
      const limit = this.limitFor(row.tier);
      if (row.used >= limit) {
        return Response.json({
          duplicate: false,
          allowed: false,
          quota: this.snapshot(row),
        } satisfies ReserveResult);
      }
      this.sql.exec("UPDATE quota SET used = used + 1 WHERE day = ?", day);
      this.sql.exec("INSERT INTO processed_requests (request_id, day, kind) VALUES (?, ?, 'regular')", requestId, day);
      return Response.json({
        duplicate: false,
        allowed: true,
        quota: this.snapshot({ ...row, used: row.used + 1 }),
      } satisfies ReserveResult);
    }

    if (url.pathname === "/release" && request.method === "POST") {
      const { requestId } = (await request.json()) as { requestId: string };
      const reservation = this.sql.exec("SELECT day, kind FROM processed_requests WHERE request_id = ?", requestId).toArray()[0];
      if (reservation) {
        const counter = reservation.kind === "bonus" ? "bonus_used" : "used";
        this.sql.exec(`UPDATE quota SET ${counter} = MAX(0, ${counter} - 1) WHERE day = ?`, reservation.day);
        if (reservation.kind === "bonus") this.sql.exec("UPDATE reward_grants SET used_by=NULL WHERE used_by=?",requestId);
        this.sql.exec("DELETE FROM processed_requests WHERE request_id = ?", requestId);
      }
      return Response.json({ ok: true });
    }

    if (url.pathname === "/consume-bonus" && request.method === "POST") {
      const { requestId } = (await request.json()) as { requestId: string };
      if (this.alreadyProcessed(requestId)) {
        return Response.json({ duplicate: true, allowed: true, quota: this.snapshot(this.getRow(day)) });
      }
      const row = this.getRow(day);
      const grant = this.sql.exec("SELECT transaction_id FROM reward_grants WHERE day=? AND used_by IS NULL LIMIT 1",day).toArray()[0];
      if (row.bonusUsed >= this.limits.bonus || !grant) {
        return Response.json({ duplicate: false, allowed: false, quota: this.snapshot(row), reason: grant ? "BONUS_USED" : "REWARD_PENDING" });
      }
      this.sql.exec("UPDATE quota SET bonus_used = bonus_used + 1 WHERE day = ?", day);
      this.sql.exec("UPDATE reward_grants SET used_by=? WHERE transaction_id=?",requestId,grant.transaction_id);
      this.sql.exec("INSERT INTO processed_requests (request_id, day, kind) VALUES (?, ?, 'bonus')", requestId, day);
      return Response.json({
        duplicate: false,
        allowed: true,
        quota: this.snapshot({ ...row, bonusUsed: row.bonusUsed + 1 }),
        consumeBonus: true,
      });
    }

    if (url.pathname === "/status" && request.method === "GET") {
      return Response.json({ quota: this.snapshot(this.getRow(day)) });
    }

    if (url.pathname === "/set-entitlement" && request.method === "POST") {
      let { tier, proExpiry, tokenHash, expectedTokenHash, lifetime } = (await request.json()) as { tier: Tier; proExpiry?: number; tokenHash?: string; expectedTokenHash?: string; lifetime?: boolean };
      const source = this.sql.exec("SELECT token_hash,lifetime FROM entitlement_source WHERE id=1").toArray()[0];
      if (expectedTokenHash && source?.token_hash !== expectedTokenHash) return Response.json({ok:true,skipped:true});
      if (tier !== "free" && tier !== "lifetime" && tier !== "pro") {
        return Response.json({ error: "bad tier" }, { status: 400 });
      }
      // Undefined means no new lifetime verification. An explicit false comes
      // from canonical Play ownership and must clear a refunded lifetime grant.
      const hasLifetime = lifetime === undefined ? source?.lifetime === 1 : lifetime;
      if (tier === "free" && hasLifetime) tier = "lifetime";
      this.sql.exec("INSERT INTO entitlement_source VALUES(1,?,?) ON CONFLICT(id) DO UPDATE SET token_hash=COALESCE(excluded.token_hash,entitlement_source.token_hash),lifetime=excluded.lifetime",tokenHash ?? null,hasLifetime ? 1 : 0);
      // Apply to today and carry forward: update latest row, insert today if missing.
      this.getRow(day);
      this.sql.exec("UPDATE quota SET tier = ?, pro_expiry = ? WHERE day = ?", tier, proExpiry ?? 0, day);
      return Response.json({ ok: true, quota: this.snapshot(this.getRow(day)) });
    }

    if (url.pathname === "/report" && request.method === "POST") {
      const body = (await request.json()) as {
        requestId?: string;
        category: string;
        detail?: string;
        excerpt?: string;
      };
      this.sql.exec(
        "INSERT INTO reports (created_at, request_id, category, detail, excerpt) VALUES (?, ?, ?, ?, ?)",
        Date.now(),
        body.requestId ?? null,
        String(body.category ?? "other").slice(0, 32),
        (body.detail ?? "").slice(0, 1000),
        (body.excerpt ?? "").slice(0, 1000),
      );
      return Response.json({ ok: true });
    }

    return Response.json({ error: "not found" }, { status: 404 });
  }
}
