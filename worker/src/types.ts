// Shared types for the Pet Companion AI Worker.

export interface Env extends CloudflareBindings {
  GOOGLE_SERVICE_ACCOUNT_JSON?: string;
  INTEGRITY_REQUIRED?: string;
  REPORTS_ADMIN_TOKEN?: string;
}

export type Tier = "free" | "lifetime" | "pro";

export interface ChatMessage {
  role: "user" | "assistant" | "system";
  content: string;
}

export interface PetContext {
  name?: string;
  species?: string;
  breed?: string;
  ageYears?: number | string;
  ageMonths?: number;
  weightKg?: number | string;
  summary7d?: string;
  overdue?: string[];
  recentEvents?: string[];
}

export interface ChatRequest {
  requestId: string;
  installationId: string;
  petId?: number | string;
  sessionId?: string;
  message: string;
  petContext?: PetContext;
  recentMessages?: ChatMessage[];
  langHint?: string;
  languageMode?: string;
  appLanguage?: string;
  stream?: boolean;
  /** New clients send "structured" and receive a validated AiAnswer. */
  answerFormat?: string;
  /** Live Play purchase so chat can attach Pro before reserving quota. */
  purchaseToken?: string;
  subscriptionId?: string;
}

export interface QuotaSnapshot {
  tier: Tier;
  limit: number;
  used: number;
  remaining: number;
  bonusRemaining: number;
  bonusReady?: boolean;
  resetAt: string;
}

export interface ReserveResult {
  duplicate: boolean;
  allowed: boolean;
  quota: QuotaSnapshot;
  consumeBonus?: boolean;
  reason?: string;
}

export type ReportCategory = "unsafe" | "incorrect" | "offensive" | "other";

export interface ReportRequest {
  installationId: string;
  requestId?: string;
  category: ReportCategory;
  detail?: string;
  messageExcerpt?: string;
}
