import { createPublicKey, verify } from "node:crypto";
import type { Env } from "../types.js";
import { callDo } from "../quota/client.js";
import { isValidId } from "../security/validation.js";
import { decode64 } from "../security/crypto.js";

export async function handleReward(request: Request, env: Env): Promise<Response> {
  const url=new URL(request.url), query=url.search.slice(1);
  if (query.length > 8192) return Response.json({error:"INVALID_REWARD"},{status:400});
  // Verify the original encoded bytes. Re-serializing URLSearchParams changes signed content.
  const match=query.match(/^(.*)&signature=([^&]+)&key_id=(\d+)$/);
  if (!match) return Response.json({error:"INVALID_REWARD"},{status:400});
  const params=url.searchParams;
  if (Array.from(params.keys()).some(key=>params.getAll(key).length !== 1)) return Response.json({error:"INVALID_REWARD"},{status:400});
  const installation=params.get("user_id"), transaction=params.get("transaction_id");
  const timestamp=Number(params.get("timestamp"));
  if (!installation || !isValidId(installation) || !transaction || !/^[A-Za-z0-9_-]{8,256}$/.test(transaction) ||
      !Number.isFinite(timestamp) || timestamp > Date.now()+30_000 || Date.now()-timestamp > 3_600_000 ||
      !env.ADMOB_REWARDED_UNIT || params.get("ad_unit") !== env.ADMOB_REWARDED_UNIT || Number(params.get("reward_amount")) <= 0) return Response.json({error:"INVALID_REWARD"},{status:400});
  try {
    const response=await fetch("https://www.gstatic.com/admob/reward/verifier-keys.json",{cf:{cacheTtl:3600},signal:AbortSignal.timeout(10_000)});
    if (!response.ok) return Response.json({error:"REWARD_KEYS_UNAVAILABLE"},{status:503});
    const {keys}=await response.json() as {keys:Array<{keyId:number;pem:string}>};
    const publicKey=keys.find(key=>String(key.keyId) === match[3]);
    if (!publicKey || !verify("sha256",new TextEncoder().encode(match[1]),createPublicKey(publicKey.pem),decode64(decodeURIComponent(match[2])))) return Response.json({error:"INVALID_SIGNATURE"},{status:403});
    const grant=await callDo(env,installation,"/grant-bonus",{transactionId:transaction});
    return grant.ok ? Response.json({ok:true}) : Response.json({error:"REWARD_UNAVAILABLE"},{status:503});
  } catch { return Response.json({error:"REWARD_UNAVAILABLE"},{status:503}); }
}
