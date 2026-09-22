import type { Brand, Channel, TicketStatus, TransactionStatus, VoucherStatus } from './types'

const eurFormat = new Intl.NumberFormat('hr-HR', { style: 'currency', currency: 'EUR' })
const dateTimeFormat = new Intl.DateTimeFormat('hr-HR', { dateStyle: 'short', timeStyle: 'short' })

export const eur = (value: number) => eurFormat.format(value)

export const dateTime = (iso: string | null | undefined) => (iso ? dateTimeFormat.format(new Date(iso)) : '—')

/** 123456789012 -> 1234-5678-9012 */
export const formatCode = (code: string) => code.replace(/(\d{4})(?=\d)/g, '$1-')

export const maskCode = (code: string) => `••••-••••-${code.slice(-4)}`

export const VOUCHER_STATUS: Record<VoucherStatus, string> = {
  AVAILABLE: 'Na zalihi',
  SOLD: 'Aktivan',
  REDEEMED: 'Iskorišten',
  BLOCKED: 'Blokiran',
}

export const TX_STATUS: Record<TransactionStatus, string> = {
  COMPLETED: 'Završena',
  REFUNDED: 'Vraćen novac',
}

export const TICKET_STATUS: Record<TicketStatus, string> = {
  OPEN: 'Otvorena',
  RESOLVED: 'Riješena',
}

export const CHANNEL: Record<Channel, string> = {
  WEB: 'Web',
  POS: 'Kiosk',
}

export const BRAND: Record<Brand, string> = {
  A1: 'A1',
  HT: 'HT',
  STEAM: 'Steam',
  SONY: 'PlayStation',
  XBOX: 'Xbox',
}

export const errorMessage = (e: unknown) => (e instanceof Error ? e.message : String(e))
