import type { Env } from "../types.js";
import { registry } from "../security/RegistryDO.js";
import { requireIntegrity } from "../security/integrity.js";
import { callDo, doStatus } from "../quota/client.js";
import { isValidId } from "../security/validation.js";
import { sha256 } from "../security/crypto.js";

export async function handleDataDelete(request:Request,env:Env):Promise<Response> {
  const failure=await requireIntegrity(request,env); if(failure) return failure;
  const {installationId}=await request.json() as {installationId:string};
  if (!isValidId(installationId)) return Response.json({error:"INVALID_INSTALLATION"},{status:400});
  const central=await registry(env,"/delete",{installationId});
  const local=await callDo(env,installationId,"/delete-data",{});
  if (!central.ok || !local.ok) return Response.json({error:"DELETION_PENDING"},{status:503});
  return Response.json({deleted:true,retained:"Anonymous daily anti-abuse counters, receipt markers and installation authentication proof"});
}
export async function handleQuotaStatus(request:Request,env:Env):Promise<Response> {
  if (request.headers.has("x-play-integrity")) {
    const failure=await requireIntegrity(request,env); if(failure) return failure;
  }
  const {installationId}=await request.json() as {installationId:string};
  const secret=request.headers.get("x-installation-secret");
  if (!isValidId(installationId) || !secret || !/^[A-Za-z0-9_-]{43}$/.test(secret)) return Response.json({error:"UNAUTHORIZED"},{status:401});
  const check=await callDo(env,installationId,"/authenticate",{secretHash:await sha256(secret),checkOnly:true});
  if (!check.ok) return Response.json({error:"UNAUTHORIZED"},{status:401});
  return Response.json({quota:await doStatus(env,installationId)});
}
export async function handleAdminReports(request:Request,env:Env):Promise<Response> {
  const expected=env.REPORTS_ADMIN_TOKEN, supplied=request.headers.get("authorization")?.replace(/^Bearer /,"");
  if (!expected || expected.length < 32 || !supplied || await sha256(expected) !== await sha256(supplied)) return Response.json({error:"UNAUTHORIZED"},{status:401});
  if (request.method === "GET") {
    const before=new URL(request.url).searchParams.get("before");
    return registry(env,`/reports${before ? `?before=${encodeURIComponent(before)}` : ""}`);
  }
  const body=await request.json() as {id?:string};
  if (!body.id || body.id.length > 128) return Response.json({error:"INVALID_REPORT"},{status:400});
  return registry(env,"/review",{id:body.id});
}
