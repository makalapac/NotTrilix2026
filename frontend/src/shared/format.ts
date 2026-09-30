import type {
  Brand,
  Channel,
  ProviderVoucherStatus,
  TicketStatus,
  TransactionStatus,
  VoucherStatus,
  WalletEntryType,
} from './types'

const eurFormat = new Intl.NumberFormat('hr-HR', { style: 'currency', currency: 'EUR' })
const dateTimeFormat = new Intl.DateTimeFormat('hr-HR', { dateStyle: 'short', timeStyle: 'short' })

export const eur = (value: number) => eurFormat.format(value)

/** +25,00 € / −25,00 € */
export const signedEur = (value: number) => (value < 0 ? `−${eurFormat.format(-value)}` : `+${eurFormat.format(value)}`)

export const WALLET_ENTRY: Record<WalletEntryType, string> = {
  TOPUP: 'Uplata',
  PURCHASE: 'Kupnja',
  REFUND: 'Povrat',
}

export const PROVIDER_STATUS: Record<ProviderVoucherStatus, string> = {
  NOT_ACTIVATED: 'Nije aktiviran',
  ACTIVE: 'Aktivan',
  REDEEMED: 'Iskorišten',
  BLOCKED: 'Blokiran',
}

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
  AGENT: 'Agent',
}

/** Nazivi alata agenta za prikaz (chat i audit). */
export const TOOL_LABEL: Record<string, string> = {
  getCatalog: 'Katalog',
  getMyPurchases: 'Moje kupnje',
  getMyWallet: 'Wallet i dnevni limit',
  getMyTickets: 'Moje prijave',
  checkVoucherAtProvider: 'Provjera bona kod pružatelja',
  purchaseVoucher: 'Kupnja bona',
  refundPurchase: 'Povrat novca',
  blockVoucher: 'Blokada bona',
  confirmPendingAction: 'Izvršenje potvrđene radnje',
  cancelPendingAction: 'Odustajanje od radnje',
  openSupportTicket: 'Otvaranje prijave',
  retrieve: 'Dohvat iz baze znanja',
  turn: 'Turn razgovora',
}

/** Faza 5: operacije analize i privatnosti u auditu. */
Object.assign(TOOL_LABEL, {
  analyze: 'Analiza razgovora',
  learn: 'Učenje (baza znanja)',
  consent: 'Privola',
  export: 'Izvoz podataka',
  erase: 'Brisanje razgovora',
  retention: 'Retencija (rok čuvanja)',
})

export const ANALYSIS_CATEGORY: Record<string, string> = {
  KUPNJA: 'Kupnja',
  POVRAT: 'Povrat',
  KOD_NE_RADI: 'Kod ne radi',
  BLOKADA: 'Blokada',
  INFORMACIJA: 'Informacija',
  OSTALO: 'Ostalo',
}

export const ANALYSIS_OUTCOME: Record<string, string> = {
  RIJESENO: 'Riješeno',
  NERIJESENO: 'Neriješeno',
  ESKALIRANO: 'Eskalirano',
  ODUSTAO: 'Odustao',
}

export const SENTIMENT: Record<string, string> = {
  POZITIVNO: 'Pozitivno',
  NEUTRALNO: 'Neutralno',
  NEGATIVNO: 'Negativno',
}

export const CONVERSATION_STATUS: Record<string, string> = {
  OPEN: 'Otvoren',
  ANALYZED: 'Analiziran',
  ERASED: 'Obrisan',
}

export const SUGGESTION_STATUS: Record<string, string> = {
  PROPOSED: 'Čeka odluku',
  APPROVED: 'Odobreno',
  REJECTED: 'Odbijeno',
}

export const AGENT_OUTCOME: Record<string, string> = {
  OK: 'izvršeno',
  DENIED: 'odbijeno',
  CONFIRMATION_REQUIRED: 'čeka potvrdu',
  ERROR: 'greška',
}

export const BRAND: Record<Brand, string> = {
  A1: 'A1',
  HT: 'HT',
  STEAM: 'Steam',
  SONY: 'PlayStation',
  XBOX: 'Xbox',
}

export const errorMessage = (e: unknown) => (e instanceof Error ? e.message : String(e))
