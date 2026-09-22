import type {
  Brand,
  CatalogItem,
  Customer,
  StockRow,
  Ticket,
  TicketRequest,
  Transaction,
  TransactionStatus,
  Voucher,
} from './types'

/** Greška iz API-ja: HTTP status + strojni kod (npr. OUT_OF_STOCK) + poruka za korisnika. */
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

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (init.body) headers['Content-Type'] = 'application/json'
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
  customers: () => request<Customer[]>('/api/customers'),
  catalog: () => request<CatalogItem[]>('/api/shop/catalog'),
  purchase: (customerId: number, productId: number) =>
    post<Transaction>('/api/shop/purchase', { customerId, productId }),
  myTransactions: (customerId: number) => request<Transaction[]>(`/api/shop/customers/${customerId}/transactions`),

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
    stock: () => request<StockRow[]>('/api/admin/stock'),
    tickets: () => request<Ticket[]>('/api/admin/tickets'),
    createTicket: (ticket: TicketRequest) => post<Ticket>('/api/admin/tickets', ticket),
    resolveTicket: (id: number, resolution: string) => post<Ticket>(`/api/admin/tickets/${id}/resolve`, { resolution }),
  },
}
