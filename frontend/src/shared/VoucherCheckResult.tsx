import type { VoucherCheck } from './types'
import { PROVIDER_STATUS, dateTime } from './format'

/** Inline display of the service provider's response (used by shop and admin). */
export function VoucherCheckResult({ check }: { check: VoucherCheck | 'loading' }) {
  if (check === 'loading') return <div className="check-result muted">Provjera kod pružatelja…</div>
  return (
    <div className="check-result">
      <span className={`badge ${check.providerStatus.toLowerCase()}`}>{PROVIDER_STATUS[check.providerStatus]}</span>{' '}
      {check.message}
      <div className="meta">
        {check.providerName} · ref. {check.providerReference} · {check.latencyMs} ms · provjereno {dateTime(check.checkedAt)}
      </div>
    </div>
  )
}
