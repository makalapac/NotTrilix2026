import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '../shared/api'
import type { Brand, CatalogItem, Category, Customer, Transaction } from '../shared/types'
import { CHANNEL, TX_STATUS, VOUCHER_STATUS, dateTime, errorMessage, eur, formatCode, maskCode } from '../shared/format'

const CATEGORIES: { key: Category; title: string; blurb: string }[] = [
  { key: 'TELEKOM', title: 'Telekom bonovi', blurb: 'nadoplata mobilnog računa' },
  { key: 'GAMING', title: 'Gaming bonovi', blurb: 'kredit za igre i sadržaj' },
]

const STORAGE_KEY = 'nottrilix.customerId'

function groupByBrand(items: CatalogItem[]): [Brand, CatalogItem[]][] {
  const groups = new Map<Brand, CatalogItem[]>()
  for (const item of items) groups.set(item.brand, [...(groups.get(item.brand) ?? []), item])
  return [...groups.entries()]
}

export default function App() {
  const [customers, setCustomers] = useState<Customer[]>([])
  const [customerId, setCustomerId] = useState<number | null>(null)
  const [catalog, setCatalog] = useState<CatalogItem[]>([])
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [lastPurchase, setLastPurchase] = useState<Transaction | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [revealed, setRevealed] = useState<Record<number, boolean>>({})

  const customer = useMemo(() => customers.find((c) => c.id === customerId) ?? null, [customers, customerId])

  const loadCatalog = useCallback(() => api.catalog().then(setCatalog), [])
  const loadTransactions = useCallback((id: number) => api.myTransactions(id).then(setTransactions), [])

  useEffect(() => {
    let remembered: number | null = null
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      remembered = stored ? Number(stored) : null
    } catch {
      /* localStorage nedostupan - ignoriraj */
    }
    api
      .customers()
      .then((list) => {
        setCustomers(list)
        setCustomerId(list.some((c) => c.id === remembered) ? remembered : (list[0]?.id ?? null))
      })
      .catch((e) => setError(errorMessage(e)))
    loadCatalog().catch((e) => setError(errorMessage(e)))
  }, [loadCatalog])

  useEffect(() => {
    if (customerId == null) return
    try {
      localStorage.setItem(STORAGE_KEY, String(customerId))
    } catch {
      /* ignoriraj */
    }
    setLastPurchase(null)
    setError(null)
    loadTransactions(customerId).catch((e) => setError(errorMessage(e)))
  }, [customerId, loadTransactions])

  async function buy(item: CatalogItem) {
    if (customerId == null || busy) return
    setBusy(true)
    setError(null)
    try {
      const tx = await api.purchase(customerId, item.productId)
      setLastPurchase(tx)
      await Promise.all([loadCatalog(), loadTransactions(customerId)])
    } catch (e) {
      setError(errorMessage(e))
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
        <label className="picker">
          Kupac
          <select value={customerId ?? ''} onChange={(e) => setCustomerId(Number(e.target.value))}>
            {customers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
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
                        disabled={busy || item.inStock === 0 || customerId == null}
                        onClick={() => buy(item)}
                        title={item.inStock === 0 ? 'Rasprodano' : `Kupi ${item.name} ${eur(item.denominationEur)}`}
                      >
                        <span className="amount">{eur(item.denominationEur)}</span>
                        <span className="stock">{item.inStock === 0 ? 'rasprodano' : `još ${item.inStock}`}</span>
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
            Moje kupnje
            {customer && (
              <small>
                {customer.name} · {customer.email}
              </small>
            )}
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
