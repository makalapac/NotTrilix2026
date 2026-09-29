import type {
  AgentChatResponse,
  AuditEvent,
  AuthUser,
  Brand,
  CatalogItem,
  Customer,
  KbHit,
  KbIngestResult,
  KbStatus,
  LoginResponse,
  PurchaseResponse,
  StockRow,
  Ticket,
  TicketRequest,
  Transaction,
  TransactionStatus,
  Voucher,
  VoucherCheck,
  Wallet,
} from './types'

/** Greška iz API-ja: HTTP status + strojni kod (npr. OUT_OF_STOCK, UNAUTHORIZED) + poruka za korisnika. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

// Bearer token u localStorage; svaka aplikacija (shop / admin) ima svoj ključ pa prijave ne smetaju jedna drugoj.
let storageKey = 'nottrilix.token'

export function configureAuth(key: string) {
  storageKey = key
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(storageKey)
  } catch {
    return null
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(storageKey, token)
    else localStorage.removeItem(storageKey)
  } catch {
    /* localStorage nedostupan - token živi samo do reloada */
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (init.body) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(path, { ...init, headers })
  const text = await res.text()
  const body = text ? JSON.parse(text) : null
  if (!res.ok) {
    throw new ApiError(res.status, body?.error ?? `HTTP_${res.status}`, body?.message ?? `HTTP ${res.status}`)
  }
  return body as T
}

function post<T>(path: string, payload?: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', body: payload === undefined ? undefined : JSON.stringify(payload) })
}

export interface TransactionFilter {
  status?: TransactionStatus
  brand?: Brand
  customerId?: number
}

export const api = {
  auth: {
    customerLogin: (email: string, password: string) =>
      post<LoginResponse>('/api/auth/customer/login', { email, password }),
    adminLogin: (username: string, password: string) =>
      post<LoginResponse>('/api/auth/admin/login', { username, password }),
    me: () => request<AuthUser>('/api/auth/me'),
    logout: () => post<void>('/api/auth/logout'),
  },

  catalog: () => request<CatalogItem[]>('/api/shop/catalog'),
  purchase: (productId: number) => post<PurchaseResponse>('/api/shop/purchase', { productId }),
  myTransactions: () => request<Transaction[]>('/api/shop/transactions'),
  wallet: () => request<Wallet>('/api/shop/wallet'),
  topUpWallet: (amountEur: number) => post<Wallet>('/api/shop/wallet/topup', { amountEur }),
  checkVoucher: (serial: string) => request<VoucherCheck>(`/api/shop/vouchers/${encodeURIComponent(serial)}/check`),
  agentChat: (conversationId: string | null, message: string) =>
    post<AgentChatResponse>('/api/agent/chat', { conversationId, message }),

  customers: () => request<Customer[]>('/api/customers'),

  admin: {
    transactions: (filter: TransactionFilter = {}) => {
      const q = new URLSearchParams()
      if (filter.status) q.set('status', filter.status)
      if (filter.brand) q.set('brand', filter.brand)
      if (filter.customerId) q.set('customerId', String(filter.customerId))
      const qs = q.toString()
      return request<Transaction[]>(`/api/admin/transactions${qs ? `?${qs}` : ''}`)
    },
    refund: (id: number) => post<Transaction>(`/api/admin/transactions/${id}/refund`),
    blockVoucher: (serial: string) => post<Voucher>(`/api/admin/vouchers/${encodeURIComponent(serial)}/block`),
    checkVoucher: (serial: string) => request<VoucherCheck>(`/api/admin/vouchers/${encodeURIComponent(serial)}/check`),
    stock: () => request<StockRow[]>('/api/admin/stock'),
    customerWallet: (customerId: number) => request<Wallet>(`/api/admin/customers/${customerId}/wallet`),
    topUpWallet: (customerId: number, amountEur: number, note: string) =>
      post<Wallet>(`/api/admin/customers/${customerId}/wallet/topup`, { amountEur, note }),
    tickets: () => request<Ticket[]>('/api/admin/tickets'),
    createTicket: (ticket: TicketRequest) => post<Ticket>('/api/admin/tickets', ticket),
    resolveTicket: (id: number, resolution: string) => post<Ticket>(`/api/admin/tickets/${id}/resolve`, { resolution }),
    audit: (filter: { conversationId?: string; customerId?: number; limit?: number } = {}) => {
      const q = new URLSearchParams()
      if (filter.conversationId) q.set('conversationId', filter.conversationId)
      if (filter.customerId) q.set('customerId', String(filter.customerId))
      if (filter.limit) q.set('limit', String(filter.limit))
      const qs = q.toString()
      return request<AuditEvent[]>(`/api/admin/audit${qs ? `?${qs}` : ''}`)
    },
    kb: {
      search: (query: string, topK?: number) =>
        request<KbHit[]>(`/api/admin/kb/search?q=${encodeURIComponent(query)}${topK ? `&topK=${topK}` : ''}`),
      status: () => request<KbStatus>('/api/admin/kb/status'),
      ingest: () => post<KbIngestResult>('/api/admin/kb/ingest?force=true'),
    },
  },
}

/** 401 = token istekao / ne vrijedi (npr. restart servera) -> aplikacija vraća korisnika na prijavu. */
export const isUnauthorized = (e: unknown) => e instanceof ApiError && e.status === 401
