// Oblici koje vraća API (zrcalo Java recorda u com.nottrilix.api.dto)

export type Category = 'TELEKOM' | 'GAMING'
export type Brand = 'A1' | 'HT' | 'STEAM' | 'SONY' | 'XBOX'
export type VoucherStatus = 'AVAILABLE' | 'SOLD' | 'REDEEMED' | 'BLOCKED'
export type TransactionStatus = 'COMPLETED' | 'REFUNDED'
export type TicketStatus = 'OPEN' | 'RESOLVED'
export type Channel = 'WEB' | 'POS'

export interface CatalogItem {
  productId: number
  category: Category
  brand: Brand
  brandName: string
  name: string
  denominationEur: number
  inStock: number
}

export interface Customer {
  id: number
  name: string
  email: string
  phone: string | null
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
