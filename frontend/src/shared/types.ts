// Oblici koje vraća API (zrcalo Java recorda u com.nottrilix.api.dto)

export type Category = 'TELEKOM' | 'GAMING'
export type Brand = 'A1' | 'HT' | 'STEAM' | 'SONY' | 'XBOX'
export type VoucherStatus = 'AVAILABLE' | 'SOLD' | 'REDEEMED' | 'BLOCKED'
export type TransactionStatus = 'COMPLETED' | 'REFUNDED'
export type TicketStatus = 'OPEN' | 'RESOLVED'
export type Channel = 'WEB' | 'POS' | 'AGENT'
export type AgentOutcome = 'OK' | 'DENIED' | 'CONFIRMATION_REQUIRED' | 'ERROR'

/** Jedan poziv alata koji je agent napravio u turnu. */
export interface AgentToolCall {
  tool: string
  input: string
  outcome: AgentOutcome
  durationMs: number
}

/** Odlomak baze znanja koji je agent dobio uz pitanje (RAG). */
export interface AgentSource {
  source: string
  title: string
  section: string
  score: number
}

export interface AgentChatResponse {
  conversationId: string
  reply: string
  toolCalls: AgentToolCall[]
  sources: AgentSource[]
  durationMs: number
  promptTokens: number | null
  completionTokens: number | null
  /** Sažetak radnje koja čeka potvrdu kupca; null ako ništa ne čeka. */
  pendingAction: string | null
}

export interface AuditEvent {
  id: number
  createdAt: string
  conversationId: string | null
  customerId: number | null
  customerName: string | null
  channel: string
  eventType: 'TOOL_CALL' | 'KB_RETRIEVAL' | 'CHAT_TURN'
  operation: string
  input: string | null
  outcome: AgentOutcome
  detail: string | null
  durationMs: number | null
  promptTokens: number | null
  completionTokens: number | null
}
export type Realm = 'CUSTOMER' | 'ADMIN'
export type WalletEntryType = 'TOPUP' | 'PURCHASE' | 'REFUND'
export type ProviderVoucherStatus = 'NOT_ACTIVATED' | 'ACTIVE' | 'REDEEMED' | 'BLOCKED'

/** Rezultat (fiktivne) provjere bona kod pružatelja usluge. */
export interface VoucherCheck {
  serial: string
  brand: Brand
  providerName: string
  providerStatus: ProviderVoucherStatus
  localStatus: VoucherStatus
  redeemedAt: string | null
  checkedAt: string
  providerReference: string
  latencyMs: number
  message: string
}

export interface AuthUser {
  realm: Realm
  id: number
  name: string
  /** e-mail kupca ili korisničko ime admina */
  login: string
}

export interface LoginResponse {
  token: string
  user: AuthUser
}

export interface CatalogItem {
  productId: number
  category: Category
  brand: Brand
  brandName: string
  name: string
  denominationEur: number
  available: boolean
}

export interface Customer {
  id: number
  name: string
  email: string
  phone: string | null
  walletBalanceEur: number
}

export interface Voucher {
  serial: string
  code: string
  status: VoucherStatus
  soldAt: string | null
}

export interface Transaction {
  id: number
  createdAt: string
  channel: Channel
  status: TransactionStatus
  customerId: number
  customerName: string
  productId: number
  brand: Brand
  productName: string
  amountEur: number
  voucher: Voucher
  refundedAt: string | null
}

export interface PurchaseResponse {
  transaction: Transaction
  walletBalanceEur: number
}

export interface WalletEntry {
  id: number
  createdAt: string
  type: WalletEntryType
  /** + uplata / povrat, − kupnja */
  amountEur: number
  balanceAfterEur: number
  transactionId: number | null
  note: string | null
}

export interface Wallet {
  balanceEur: number
  entries: WalletEntry[]
}

export interface Ticket {
  id: number
  createdAt: string
  status: TicketStatus
  customerId: number
  customerName: string
  transactionId: number | null
  subject: string
  description: string | null
  resolution: string | null
  resolvedAt: string | null
}

/** Pogodak semantičke pretrage baze znanja (RAG). */
export interface KbHit {
  source: string
  title: string
  section: string
  category: string
  tags: string
  /** sličnost upitu, 0..1 */
  score: number
  content: string
}

export interface KbSourceStatus {
  source: string
  title: string
  chunks: number
}

export interface KbStatus {
  available: boolean
  chunks: number
  sources: KbSourceStatus[]
  lastIngest: string | null
  topK: number
  similarityThreshold: number
}

export interface KbIngestResult {
  files: number
  addedChunks: number
  skippedFiles: number
  replacedFiles: number
  totalChunks: number
}

export interface TicketRequest {
  customerId: number
  transactionId: number | null
  subject: string
  description: string
}

export interface StockRow {
  productId: number
  brand: Brand
  name: string
  denominationEur: number
  active: boolean
  available: number
  sold: number
  redeemed: number
  blocked: number
}
