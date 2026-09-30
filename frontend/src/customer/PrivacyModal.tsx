import { useCallback, useEffect, useState } from 'react'
import { api } from '../shared/api'
import type { EraseResult, PrivacyOverview } from '../shared/types'
import { dateTime, errorMessage } from '../shared/format'
import { VOICE_CONSENT_TEXT } from './ChatWidget'

/**
 * The customer's rights over their data (GDPR): what we keep and for how long, consents, export of all data (JSON) and erasure of conversations.
 * Purchases, wallet and vouchers are not erased (contract and accounting); that is stated here, not hidden.
 */
export function PrivacyModal({
  onClose,
  onFail,
  onConsentChanged,
}: {
  onClose: () => void
  onFail: (e: unknown) => void
  onConsentChanged: () => void
}) {
  const [overview, setOverview] = useState<PrivacyOverview | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmErase, setConfirmErase] = useState(false)
  const [erased, setErased] = useState<EraseResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => api.privacy.overview().then(setOverview), [])

  useEffect(() => {
    load().catch(onFail)
  }, [load, onFail])

  async function download() {
    setBusy(true)
    setError(null)
    try {
      const data = await api.privacy.exportData()
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'nottrilix-moji-podaci.json'
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function erase() {
    setBusy(true)
    setError(null)
    try {
      setErased(await api.privacy.eraseConversations())
      setConfirmErase(false)
      await load()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function withdrawVoice() {
    setBusy(true)
    setError(null)
    try {
      setOverview(await api.privacy.withdraw('VOICE'))
      onConsentChanged()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function giveVoice() {
    setBusy(true)
    setError(null)
    try {
      await api.privacy.consent('VOICE')
      await load()
      onConsentChanged()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal privacy" onClick={(e) => e.stopPropagation()}>
        <h3>Privatnost i moji podaci</h3>
        {!overview ? (
          <p className="muted">Učitavam…</p>
        ) : (
          <>
            <p>
              Razgovore s podrškom (chat i transkripte glasa) čuvamo <b>{overview.conversationRetentionDays} dana</b>; analize
              razgovora do {overview.analysisRetentionDays} dana, nakon {overview.conversationRetentionDays} dana bez veze s
              vama. Odgovore stvara AI (OpenAI); glas pretvara u tekst OpenAI, a u govor ElevenLabs. Snimke glasa se ne
              pohranjuju. Razgovori se ne koriste za učenje modela.
            </p>
            <div className="row">
              <span>Spremljenih razgovora</span>
              <b>{overview.storedConversations}</b>
            </div>
            <div className="row">
              <span>Privola za glasovni razgovor</span>
              <b>{overview.voiceConsentGiven ? 'dana' : 'nije dana'}</b>
            </div>
            {overview.consents.length > 0 && (
              <div className="consent-list muted">
                {overview.consents.map((c, i) => (
                  <div key={i}>
                    {c.type} v{c.textVersion}: dana {dateTime(c.givenAt)}
                    {c.withdrawnAt && `, povučena ${dateTime(c.withdrawnAt)}`}
                  </div>
                ))}
              </div>
            )}
            <details className="consent-text">
              <summary className="muted">Tekst privole za glas</summary>
              <p className="muted">{VOICE_CONSENT_TEXT}</p>
            </details>

            {error && <div className="banner err">{error}</div>}
            {erased && (
              <div className="banner ok">
                Obrisano: {erased.deletedMessages} poruka, {erased.erasedConversations} razgovora; {erased.anonymizedEvents} zapisa
                i {erased.anonymizedAnalyses} analiza više nije povezano s vama.
              </div>
            )}

            <div className="privacy-actions">
              <button className="btn" disabled={busy} onClick={download}>
                Preuzmi moje podatke (JSON)
              </button>
              {overview.voiceConsentGiven ? (
                <button className="btn" disabled={busy} onClick={withdrawVoice}>
                  Povuci privolu za glas
                </button>
              ) : (
                <button className="btn" disabled={busy} onClick={giveVoice}>
                  Daj privolu za glas
                </button>
              )}
              {!confirmErase ? (
                <button className="btn danger" disabled={busy || overview.storedConversations === 0} onClick={() => setConfirmErase(true)}>
                  Obriši moje razgovore
                </button>
              ) : (
                <div className="confirm-erase">
                  <span>Poruke se brišu odmah i nepovratno. Kupnje, wallet i bonovi ostaju.</span>
                  <button className="btn danger" disabled={busy} onClick={erase}>
                    Da, obriši
                  </button>
                  <button className="btn" disabled={busy} onClick={() => setConfirmErase(false)}>
                    Odustani
                  </button>
                </div>
              )}
            </div>
          </>
        )}
        <div className="actions">
          <button className="btn" onClick={onClose}>
            Zatvori
          </button>
        </div>
      </div>
    </div>
  )
}
