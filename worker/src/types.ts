// Shared types for the Pet Companion AI Worker.

export interface Env {
  AI: Ai;
  AI_QUOTA: DurableObjectNamespace;
  CONFIG: KVNamespace;
  MODEL: string;
  FREE_DAILY: string;
  LIFETIME_DAILY: string;
  PRO_DAILY: string;
  BONUS_DAILY: string;
  MAX_TOKENS: string;
  PLAY_PACKAGE_NAME: string;
  AI_WEEKLY_SKU: string;
  AI_MONTHLY_SKU?: string;
  GOOGLE_SERVICE_ACCOUNT_JSON?: string;
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
  resetAt: string;
}

export interface ReserveResult {
  duplicate: boolean;
  allowed: boolean;
  quota: QuotaSnapshot;
  consumeBonus?: boolean;
}

export type ReportCategory = "unsafe" | "incorrect" | "offensive" | "other";

export interface ReportRequest {
  installationId: string;
  requestId?: string;
  category: ReportCategory;
  detail?: string;
  messageExcerpt?: string;
}
