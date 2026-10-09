import { decode64 } from "./crypto.js";
import type { Env } from "../types.js";

type GoogleJwk = JsonWebKey & {kid?: string; alg?: string};
export async function authenticatePubSub(request: Request, env: Env): Promise<boolean> {
  if (!env.RTDN_AUDIENCE || !env.RTDN_SERVICE_ACCOUNT_EMAIL) return false;
  const token=request.headers.get("authorization")?.match(/^Bearer ([A-Za-z0-9_.-]+)$/)?.[1];
  if (!token || token.length > 8192) return false;
  try {
    const parts=token.split(".");
    if (parts.length !== 3) return false;
    const header=JSON.parse(new TextDecoder().decode(decode64(parts[0]))) as {alg?:string;kid?:string};
    const claims=JSON.parse(new TextDecoder().decode(decode64(parts[1]))) as {iss?:string;aud?:string;email?:string;email_verified?:boolean;iat?:number;exp?:number};
    const now=Math.floor(Date.now()/1000);
    if (header.alg !== "RS256" || !header.kid || !["accounts.google.com","https://accounts.google.com"].includes(claims.iss ?? "") ||
        claims.aud !== env.RTDN_AUDIENCE || claims.email !== env.RTDN_SERVICE_ACCOUNT_EMAIL || claims.email_verified !== true ||
        !Number.isFinite(claims.exp) || !Number.isFinite(claims.iat) || (claims.exp ?? 0) <= now || (claims.iat ?? Infinity) > now+30) return false;
    // Keys are public; URL is fixed, never taken from a JWT's jku/x5u headers.
    const response=await fetch("https://www.googleapis.com/oauth2/v3/certs",{cf:{cacheTtl:3600},signal:AbortSignal.timeout(10_000)});
    if (!response.ok) return false;
    const {keys}=await response.json() as {keys:GoogleJwk[]};
    const jwk=keys.find(key=>key.kid === header.kid && key.kty === "RSA" && (!key.alg || key.alg === "RS256"));
    if (!jwk) return false;
    const key=await crypto.subtle.importKey("jwk",jwk,{name:"RSASSA-PKCS1-v1_5",hash:"SHA-256"},false,["verify"]);
    return crypto.subtle.verify("RSASSA-PKCS1-v1_5",key,decode64(parts[2]),new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
  } catch { return false; }
}
