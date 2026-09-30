import { useCallback, useEffect, useState } from 'react'
import { api, getToken, isUnauthorized, setToken } from '../shared/api'
import { LoginPage } from '../shared/LoginPage'
import { VoucherCheckResult } from '../shared/VoucherCheckResult'
import { ChatWidget } from './ChatWidget'
import { PrivacyModal } from './PrivacyModal'
import type { AuthUser, Brand, CatalogItem, Category, PurchaseResponse, Transaction, VoucherCheck, Wallet } from '../shared/types'
import {
  CHANNEL,
  TX_STATUS,
  VOUCHER_STATUS,
  WALLET_ENTRY,
  dateTime,
  errorMessage,
  eur,
  formatCode,
  maskCode,
  signedEur,
} from '../shared/format'

const CATEGORIES: { key: Category; title: string; blurb: string }[] = [
  { key: 'TELEKOM', title: 'Telekom bonovi', blurb: 'nadoplata mobilnog računa' },
  { key: 'GAMING', title: 'Gaming bonovi', blurb: 'kredit za igre i sadržaj' },
]

const TOP_UP_PRESETS = [10, 20, 50, 100]
const TOP_UP_MAX = 500

function groupByBrand(items: CatalogItem[]): [Brand, CatalogItem[]][] {
  const groups = new Map<Brand, CatalogItem[]>()
  for (const item of items) groups.set(item.brand, [...(groups.get(item.brand) ?? []), item])
  return [...groups.entries()]
}

/** Login (e-mail + password), then the shop; the token is kept in localStorage and verified at startup. */
export default function App() {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [checking, setChecking] = useState(() => getToken() != null)

  useEffect(() => {
    if (!getToken()) return
    api.auth
      .me()
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setChecking(false))
  }, [])

  const signOut = useCallback(() => {
    setToken(null)
    setUser(null)
  }, [])

  if (checking) return null

  if (!user) {
    return (
      <LoginPage
        title="NotTrilix shop"
        subtitle="Prijavite se za kupnju bonova."
        identityLabel="E-mail"
        identityType="email"
        identityPlaceholder="ime.prezime@example.com"
        hint={
          <>
            Demo računi (lozinka = ime + 123):
            <br />
            ana.anic@example.com / ana123 · marko.maric@example.com / marko123 · ivana.ivic@example.com / ivana123
          </>
        }
        onLogin={async (email, password) => {
          const res = await api.auth.customerLogin(email, password)
          setToken(res.token)
          setUser(res.user)
        }}
      />
    )
  }

  return (
    <Shop
      user={user}
      onLogout={async () => {
        try {
          await api.auth.logout()
        } catch {
          /* we discard the token anyway */
        }
        signOut()
      }}
      onSessionExpired={signOut}
    />
  )
}

function Shop({ user, onLogout, onSessionExpired }: { user: AuthUser; onLogout: () => void; onSessionExpired: () => void }) {
  const [catalog, setCatalog] = useState<CatalogItem[]>([])
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [wallet, setWallet] = useState<Wallet | null>(null)
  const [pending, setPending] = useState<CatalogItem | null>(null) // voucher awaiting payment confirmation
  const [topUpOpen, setTopUpOpen] = useState(false)
  const [lastPurchase, setLastPurchase] = useState<PurchaseResponse | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [revealed, setRevealed] = useState<Record<number, boolean>>({})
  const [checks, setChecks] = useState<Record<string, VoucherCheck | 'loading'>>({})
  const [privacyOpen, setPrivacyOpen] = useState(false)
  const [voiceConsent, setVoiceConsent] = useState(false) // voice consent; the server knows the state

  const fail = useCallback(
    (e: unknown) => {
      if (isUnauthorized(e)) onSessionExpired()
      else setError(errorMessage(e))
    },
    [onSessionExpired],
  )

  const loadCatalog = useCallback(() => api.catalog().then(setCatalog), [])
  const loadTransactions = useCallback(() => api.myTransactions().then(setTransactions), [])
  const loadWallet = useCallback(() => api.wallet().then(setWallet), [])

  const loadPrivacy = useCallback(() => api.privacy.overview().then((p) => setVoiceConsent(p.voiceConsentGiven)), [])

  useEffect(() => {
    loadCatalog().catch(fail)
    loadTransactions().catch(fail)
    loadWallet().catch(fail)
    loadPrivacy().catch(fail)
  }, [loadCatalog, loadTransactions, loadWallet, loadPrivacy, fail])

  async function confirmPurchase() {
    if (!pending || busy) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const res = await api.purchase(pending.productId)
      setLastPurchase(res)
      setPending(null)
      await Promise.all([loadCatalog(), loadTransactions(), loadWallet()])
    } catch (e) {
      setPending(null)
      fail(e)
    } finally {
      setBusy(false)
    }
  }

  async function topUp(amount: number) {
    if (busy) return
    setBusy(true)
    setError(null)
    setLastPurchase(null)
    try {
      const updated = await api.topUpWallet(amount)
      setWallet(updated)
      setTopUpOpen(false)
      setNotice(`Uplata ${eur(amount)} uspješna. Novo stanje walleta: ${eur(updated.balanceEur)}.`)
    } catch (e) {
      setTopUpOpen(false)
      fail(e)
    } finally {
      setBusy(false)
    }
  }

  async function checkVoucher(serial: string) {
    setChecks((c) => ({ ...c, [serial]: 'loading' }))
    try {
      const result = await api.checkVoucher(serial)
      setChecks((c) => ({ ...c, [serial]: result }))
    } catch (e) {
      setChecks((c) => {
        const { [serial]: _removed, ...rest } = c
        return rest
      })
      fail(e)
    }
  }

  const balance = wallet?.balanceEur ?? 0

  /** The agent may have purchased, refunded or blocked: refresh everything the customer sees. */
  const reloadAfterAgent = useCallback(() => {
    setChecks({})
    Promise.all([loadCatalog(), loadTransactions(), loadWallet()]).catch(fail)
  }, [loadCatalog, loadTransactions, loadWallet, fail])

  return (
    <>
      <ChatWidget
        user={user}
        voiceConsent={voiceConsent}
        onVoiceConsent={async () => {
          await api.privacy.consent('VOICE')
          setVoiceConsent(true)
        }}
        onStateChanged={reloadAfterAgent}
        onSessionExpired={onSessionExpired}
      />
      {privacyOpen && (
        <PrivacyModal onClose={() => setPrivacyOpen(false)} onFail={fail} onConsentChanged={() => loadPrivacy().catch(fail)} />
      )}
      <header className="topbar">
        <div className="brand">
          NotTrilix <span>shop</span>
        </div>
        <div className="userbox">
          <span className="who">{user.name}</span>
          <span className="wallet-pill" title="Stanje walleta">
            Wallet: {wallet ? eur(wallet.balanceEur) : '…'}
          </span>
          <button className="btn ghost" onClick={() => setTopUpOpen(true)}>
            Nadoplati
          </button>
          <button className="btn ghost" onClick={() => setPrivacyOpen(true)} title="Moji podaci, privole, izvoz i brisanje">
            Privatnost
          </button>
          <button className="btn ghost" onClick={onLogout}>
            Odjava
          </button>
        </div>
      </header>

      {pending && wallet && (
        <ConfirmPurchaseModal
          item={pending}
          balance={balance}
          busy={busy}
          onConfirm={confirmPurchase}
          onCancel={() => setPending(null)}
          onTopUp={() => {
            setPending(null)
            setTopUpOpen(true)
          }}
        />
      )}
      {topUpOpen && (
        <TopUpModal balance={balance} busy={busy} onTopUp={topUp} onCancel={() => setTopUpOpen(false)} />
      )}

      <main className="container">
        {error && <div className="banner err">{error}</div>}
        {notice && <div className="banner ok">{notice}</div>}

        {lastPurchase && (
          <div className="banner ok">
            <strong>Kupnja uspješna!</strong> {lastPurchase.transaction.productName} za{' '}
            {eur(lastPurchase.transaction.amountEur)} naplaćeno s walleta — novo stanje {eur(lastPurchase.walletBalanceEur)}.
            <div className="code-box">
              <div>
                <span className="muted">Serijski broj</span>
                <b className="mono">{lastPurchase.transaction.voucher.serial}</b>
              </div>
              <div>
                <span className="muted">Aktivacijski kod</span>
                <b className="mono">{formatCode(lastPurchase.transaction.voucher.code)}</b>
              </div>
            </div>
          </div>
        )}

        {CATEGORIES.map((category) => (
          <section key={category.key} className="section">
            <h2>
              {category.title} <small>{category.blurb}</small>
            </h2>
            <div className="grid">
              {groupByBrand(catalog.filter((i) => i.category === category.key)).map(([brand, items]) => (
                <article key={brand} className={`card brand-${brand.toLowerCase()}`}>
                  <h3>{items[0].brandName}</h3>
                  <p className="muted">{items[0].name}</p>
                  <div className="denoms">
                    {items.map((item) => (
                      <button
                        key={item.productId}
                        className="denom"
                        disabled={busy || !item.available || !wallet}
                        onClick={() => setPending(item)}
                        title={item.available ? `Kupi ${item.name} ${eur(item.denominationEur)}` : 'Rasprodano'}
                      >
                        <span className="amount">{eur(item.denominationEur)}</span>
                        {!item.available && <span className="stock">rasprodano</span>}
                      </button>
                    ))}
                  </div>
                </article>
              ))}
            </div>
          </section>
        ))}

        <section className="section">
          <h2>
            Moj wallet <small>stanje {wallet ? eur(wallet.balanceEur) : '…'}</small>
          </h2>
          {!wallet || wallet.entries.length === 0 ? (
            <p className="muted">Još nema promjena na walletu.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Datum</th>
                    <th>Vrsta</th>
                    <th className="num">Iznos</th>
                    <th className="num">Stanje nakon</th>
                    <th>Napomena</th>
                  </tr>
                </thead>
                <tbody>
                  {wallet.entries.map((e) => (
                    <tr key={e.id}>
                      <td>{dateTime(e.createdAt)}</td>
                      <td>
                        <span className={`badge ${e.type.toLowerCase()}`}>{WALLET_ENTRY[e.type]}</span>
                      </td>
                      <td className={`num ${e.amountEur < 0 ? 'neg' : 'pos'}`}>{signedEur(e.amountEur)}</td>
                      <td className="num">{eur(e.balanceAfterEur)}</td>
                      <td className="muted">
                        {e.note}
                        {e.transactionId != null && ` (kupnja #${e.transactionId})`}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="section">
          <h2>
            Moje kupnje <small>{user.login}</small>
          </h2>
          {transactions.length === 0 ? (
            <p className="muted">Još nema kupnji.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Datum</th>
                    <th>Proizvod</th>
                    <th className="num">Iznos</th>
                    <th>Kanal</th>
                    <th>Serijski broj</th>
                    <th>Aktivacijski kod</th>
                    <th>Status</th>
                    <th>Pružatelj</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map((t) => (
                    <tr key={t.id}>
                      <td>{t.id}</td>
                      <td>{dateTime(t.createdAt)}</td>
                      <td>{t.productName}</td>
                      <td className="num">{eur(t.amountEur)}</td>
                      <td>{CHANNEL[t.channel]}</td>
                      <td className="mono">{t.voucher.serial}</td>
                      <td className="mono">
                        {revealed[t.id] ? formatCode(t.voucher.code) : maskCode(t.voucher.code)}{' '}
                        <button className="link" onClick={() => setRevealed((r) => ({ ...r, [t.id]: !r[t.id] }))}>
                          {revealed[t.id] ? 'sakrij' : 'prikaži'}
                        </button>
                      </td>
                      <td>
                        {t.status === 'REFUNDED' ? (
                          <span className="badge refunded">{TX_STATUS.REFUNDED}</span>
                        ) : (
                          <span className={`badge ${t.voucher.status.toLowerCase()}`}>{VOUCHER_STATUS[t.voucher.status]}</span>
                        )}
                      </td>
                      <td>
                        <button
                          className="btn"
                          disabled={checks[t.voucher.serial] === 'loading'}
                          onClick={() => checkVoucher(t.voucher.serial)}
                          title="Provjeri je li bon iskorišten kod pružatelja usluge"
                        >
                          Provjeri
                        </button>
                        {checks[t.voucher.serial] && <VoucherCheckResult check={checks[t.voucher.serial]} />}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>
    </>
  )
}

/** Question before purchase: charge the amount to the wallet? If there is not enough, offers a top-up (the server checks the rule regardless). */
function ConfirmPurchaseModal({
  item,
  balance,
  busy,
  onConfirm,
  onCancel,
  onTopUp,
}: {
  item: CatalogItem
  balance: number
  busy: boolean
  onConfirm: () => void
  onCancel: () => void
  onTopUp: () => void
}) {
  const price = item.denominationEur
  const after = balance - price
  const enough = after >= 0

  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Naplata s walleta</h3>
        <p>
          Kupujete <b>{item.name} {eur(price)}</b>. Želite li naplatiti taj iznos s vašeg walleta?
        </p>
        <div className="row">
          <span>Stanje walleta</span>
          <b>{eur(balance)}</b>
        </div>
        <div className="row">
          <span>Cijena bona</span>
          <b>− {eur(price)}</b>
        </div>
        <div className="row total">
          <span>Stanje nakon kupnje</span>
          <b className={enough ? '' : 'neg'}>{eur(after)}</b>
        </div>
        {!enough && <div className="banner err">Nedovoljno sredstava — nadoplatite wallet za najmanje {eur(price - balance)}.</div>}
        <div className="actions">
          <button className="btn" onClick={onCancel} disabled={busy}>
            Odustani
          </button>
          {enough ? (
            <button className="btn primary" onClick={onConfirm} disabled={busy}>
              {busy ? 'Naplata…' : 'Da, naplati s walleta'}
            </button>
          ) : (
            <button className="btn primary" onClick={onTopUp}>
              Nadoplati wallet
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

/** Wallet top-up - in the demo a simulated card payment (the amount is booked immediately). */
function TopUpModal({
  balance,
  busy,
  onTopUp,
  onCancel,
}: {
  balance: number
  busy: boolean
  onTopUp: (amount: number) => void
  onCancel: () => void
}) {
  const [amount, setAmount] = useState(20)
  const valid = Number.isFinite(amount) && amount >= 1 && amount <= TOP_UP_MAX

  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Nadoplata walleta</h3>
        <p className="muted">
          Simulirano plaćanje karticom — iznos se odmah dodaje na wallet. Trenutno stanje: {eur(balance)}.
        </p>
        <div className="amounts">
          {TOP_UP_PRESETS.map((preset) => (
            <button key={preset} className={`btn ${amount === preset ? 'primary' : ''}`} onClick={() => setAmount(preset)}>
              {eur(preset)}
            </button>
          ))}
        </div>
        <label className="field">
          Drugi iznos (1 – {TOP_UP_MAX} €)
          <input type="number" min={1} max={TOP_UP_MAX} step={1} value={amount} onChange={(e) => setAmount(Number(e.target.value))} />
        </label>
        <div className="actions">
          <button className="btn" onClick={onCancel} disabled={busy}>
            Odustani
          </button>
          <button className="btn primary" disabled={busy || !valid} onClick={() => onTopUp(amount)}>
            {busy ? 'Uplata…' : `Uplati ${valid ? eur(amount) : ''}`}
          </button>
        </div>
      </div>
    </div>
  )
}
