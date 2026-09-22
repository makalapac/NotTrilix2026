import { useCallback, useEffect, useState } from 'react'
import { api, getToken, isUnauthorized, setToken } from '../shared/api'
import { LoginPage } from '../shared/LoginPage'
import type { AuthUser, Brand, CatalogItem, Category, Transaction } from '../shared/types'
import { CHANNEL, TX_STATUS, VOUCHER_STATUS, dateTime, errorMessage, eur, formatCode, maskCode } from '../shared/format'

const CATEGORIES: { key: Category; title: string; blurb: string }[] = [
  { key: 'TELEKOM', title: 'Telekom bonovi', blurb: 'nadoplata mobilnog računa' },
  { key: 'GAMING', title: 'Gaming bonovi', blurb: 'kredit za igre i sadržaj' },
]

function groupByBrand(items: CatalogItem[]): [Brand, CatalogItem[]][] {
  const groups = new Map<Brand, CatalogItem[]>()
  for (const item of items) groups.set(item.brand, [...(groups.get(item.brand) ?? []), item])
  return [...groups.entries()]
}

/** Prijava (e-mail + lozinka) pa shop; token se pamti u localStorage i provjerava na startu. */
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
          /* token ionako odbacujemo */
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
  const [lastPurchase, setLastPurchase] = useState<Transaction | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [revealed, setRevealed] = useState<Record<number, boolean>>({})

  const fail = useCallback(
    (e: unknown) => {
      if (isUnauthorized(e)) onSessionExpired()
      else setError(errorMessage(e))
    },
    [onSessionExpired],
  )

  const loadCatalog = useCallback(() => api.catalog().then(setCatalog), [])
  const loadTransactions = useCallback(() => api.myTransactions().then(setTransactions), [])

  useEffect(() => {
    loadCatalog().catch(fail)
    loadTransactions().catch(fail)
  }, [loadCatalog, loadTransactions, fail])

  async function buy(item: CatalogItem) {
    if (busy) return
    setBusy(true)
    setError(null)
    try {
      const tx = await api.purchase(item.productId)
      setLastPurchase(tx)
      await Promise.all([loadCatalog(), loadTransactions()])
    } catch (e) {
      fail(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <header className="topbar">
        <div className="brand">
          NotTrilix <span>shop</span>
        </div>
        <div className="userbox">
          <span className="who">{user.name}</span>
          <button className="btn ghost" onClick={onLogout}>
            Odjava
          </button>
        </div>
      </header>

      <main className="container">
        {error && <div className="banner err">{error}</div>}

        {lastPurchase && (
          <div className="banner ok">
            <strong>Kupnja uspješna!</strong> {lastPurchase.productName} za {eur(lastPurchase.amountEur)}.
            <div className="code-box">
              <div>
                <span className="muted">Serijski broj</span>
                <b className="mono">{lastPurchase.voucher.serial}</b>
              </div>
              <div>
                <span className="muted">Aktivacijski kod</span>
                <b className="mono">{formatCode(lastPurchase.voucher.code)}</b>
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
                        disabled={busy || !item.available}
                        onClick={() => buy(item)}
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
            Moje kupnje <small>{user.login}</small>
          </h2>
          {transactions.length === 0 ? (
            <p className="muted">Još nema kupnji.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Datum</th>
                    <th>Proizvod</th>
                    <th className="num">Iznos</th>
                    <th>Kanal</th>
                    <th>Serijski broj</th>
                    <th>Aktivacijski kod</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map((t) => (
                    <tr key={t.id}>
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
