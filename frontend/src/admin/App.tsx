import { useCallback, useEffect, useState } from 'react'
import { api, getToken, isUnauthorized, setToken } from '../shared/api'
import { LoginPage } from '../shared/LoginPage'
import { VoucherCheckResult } from '../shared/VoucherCheckResult'
import type {
  AuthUser,
  Brand,
  Customer,
  StockRow,
  Ticket,
  Transaction,
  TransactionStatus,
  VoucherCheck,
  Wallet,
} from '../shared/types'
import {
  BRAND,
  CHANNEL,
  TICKET_STATUS,
  TX_STATUS,
  VOUCHER_STATUS,
  WALLET_ENTRY,
  dateTime,
  errorMessage,
  eur,
  formatCode,
  signedEur,
} from '../shared/format'

type Tab = 'transactions' | 'customers' | 'stock' | 'tickets'
type Notice = { kind: 'ok' | 'err'; text: string } | null
/** Izvrši akciju; uspjeh/greška ide u banner na vrhu (401 = natrag na prijavu). */
type Runner = (successText: string, action: () => Promise<unknown>) => Promise<void>
/** Prijavi grešku iz učitavanja (bez poruke o uspjehu). */
type Reporter = (e: unknown) => void

const TAB_LABEL: Record<Tab, string> = { transactions: 'Transakcije', customers: 'Kupci', stock: 'Zalihe', tickets: 'Podrška' }
const BRANDS: Brand[] = ['A1', 'HT', 'STEAM', 'SONY', 'XBOX']

/** Prijava (korisničko ime + lozinka) pa admin konzola. */
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
        title="NotTrilix admin"
        subtitle="Prijava za podršku i administraciju."
        identityLabel="Korisničko ime"
        identityType="text"
        identityPlaceholder="admin"
        hint={<>Demo račun: admin / admin123</>}
        onLogin={async (username, password) => {
          const res = await api.auth.adminLogin(username, password)
          setToken(res.token)
          setUser(res.user)
        }}
      />
    )
  }

  return (
    <AdminConsole
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

function AdminConsole({
  user,
  onLogout,
  onSessionExpired,
}: {
  user: AuthUser
  onLogout: () => void
  onSessionExpired: () => void
}) {
  const [tab, setTab] = useState<Tab>('transactions')
  const [notice, setNotice] = useState<Notice>(null)
  const [customers, setCustomers] = useState<Customer[]>([])

  const report: Reporter = useCallback(
    (e) => {
      if (isUnauthorized(e)) onSessionExpired()
      else setNotice({ kind: 'err', text: errorMessage(e) })
    },
    [onSessionExpired],
  )

  const run: Runner = async (successText, action) => {
    try {
      await action()
      setNotice({ kind: 'ok', text: successText })
    } catch (e) {
      report(e)
    }
  }

  const loadCustomers = useCallback(() => api.customers().then(setCustomers), [])

  useEffect(() => {
    loadCustomers().catch(report)
  }, [loadCustomers, report])

  return (
    <>
      <header className="topbar admin">
        <div className="brand">
          NotTrilix <span>admin</span>
        </div>
        <nav className="tabs">
          {(Object.keys(TAB_LABEL) as Tab[]).map((t) => (
            <button
              key={t}
              className={tab === t ? 'active' : ''}
              onClick={() => {
                setTab(t)
                setNotice(null)
              }}
            >
              {TAB_LABEL[t]}
            </button>
          ))}
        </nav>
        <div className="userbox">
          <span className="who">{user.name}</span>
          <button className="btn ghost" onClick={onLogout}>
            Odjava
          </button>
        </div>
      </header>

      <main className="container">
        {notice && <div className={`banner ${notice.kind}`}>{notice.text}</div>}
        {tab === 'transactions' && <TransactionsTab customers={customers} run={run} report={report} />}
        {tab === 'customers' && <CustomersTab customers={customers} reloadCustomers={loadCustomers} run={run} report={report} />}
        {tab === 'stock' && <StockTab run={run} report={report} />}
        {tab === 'tickets' && <TicketsTab customers={customers} run={run} report={report} />}
      </main>
    </>
  )
}

function TransactionsTab({ customers, run, report }: { customers: Customer[]; run: Runner; report: Reporter }) {
  const [rows, setRows] = useState<Transaction[]>([])
  const [status, setStatus] = useState<TransactionStatus | ''>('')
  const [brand, setBrand] = useState<Brand | ''>('')
  const [customerId, setCustomerId] = useState<number | ''>('')
  const [checks, setChecks] = useState<Record<string, VoucherCheck | 'loading'>>({})

  const checkVoucher = async (serial: string) => {
    setChecks((c) => ({ ...c, [serial]: 'loading' }))
    try {
      const result = await api.admin.checkVoucher(serial)
      setChecks((c) => ({ ...c, [serial]: result }))
    } catch (e) {
      setChecks((c) => {
        const { [serial]: _removed, ...rest } = c
        return rest
      })
      report(e)
    }
  }

  const load = useCallback(
    () =>
      api.admin
        .transactions({
          status: status || undefined,
          brand: brand || undefined,
          customerId: customerId || undefined,
        })
        .then(setRows),
    [status, brand, customerId],
  )

  useEffect(() => {
    load().catch(report)
  }, [load, report])

  const canRefund = (t: Transaction) => t.status === 'COMPLETED'
  const canBlock = (t: Transaction) => t.voucher.status === 'SOLD' || t.voucher.status === 'AVAILABLE'

  return (
    <section className="section">
      <h2>
        Transakcije <small>{rows.length} redaka</small>
      </h2>
      <div className="filters">
        <select value={status} onChange={(e) => setStatus(e.target.value as TransactionStatus | '')}>
          <option value="">Svi statusi</option>
          <option value="COMPLETED">{TX_STATUS.COMPLETED}</option>
          <option value="REFUNDED">{TX_STATUS.REFUNDED}</option>
        </select>
        <select value={brand} onChange={(e) => setBrand(e.target.value as Brand | '')}>
          <option value="">Svi brendovi</option>
          {BRANDS.map((b) => (
            <option key={b} value={b}>
              {BRAND[b]}
            </option>
          ))}
        </select>
        <select value={customerId} onChange={(e) => setCustomerId(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Svi kupci</option>
          {customers.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <button className="btn" onClick={() => run('Osvježeno', load)}>
          Osvježi
        </button>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Datum</th>
              <th>Kupac</th>
              <th>Proizvod</th>
              <th className="num">Iznos</th>
              <th>Kanal</th>
              <th>Bon</th>
              <th>Status bona</th>
              <th>Transakcija</th>
              <th>Akcije</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((t) => (
              <tr key={t.id}>
                <td>{t.id}</td>
                <td>{dateTime(t.createdAt)}</td>
                <td>{t.customerName}</td>
                <td>{t.productName}</td>
                <td className="num">{eur(t.amountEur)}</td>
                <td>{CHANNEL[t.channel]}</td>
                <td className="mono">
                  {t.voucher.serial}
                  <br />
                  <span className="muted">{formatCode(t.voucher.code)}</span>
                </td>
                <td>
                  <span className={`badge ${t.voucher.status.toLowerCase()}`}>{VOUCHER_STATUS[t.voucher.status]}</span>
                  {checks[t.voucher.serial] && <VoucherCheckResult check={checks[t.voucher.serial]} />}
                </td>
                <td>
                  <span className={`badge ${t.status.toLowerCase()}`}>{TX_STATUS[t.status]}</span>
                  {t.refundedAt && <div className="muted">{dateTime(t.refundedAt)}</div>}
                </td>
                <td>
                  <div className="actions">
                    <button
                      className="btn danger"
                      disabled={!canRefund(t)}
                      onClick={() =>
                        run(
                          `Povrat ${eur(t.amountEur)} za transakciju #${t.id} vraćen na wallet kupca, bon ${t.voucher.serial} blokiran`,
                          async () => {
                            await api.admin.refund(t.id)
                            await load()
                          },
                        )
                      }
                    >
                      Povrat
                    </button>
                    <button
                      className="btn"
                      disabled={!canBlock(t)}
                      onClick={() =>
                        run(`Bon ${t.voucher.serial} blokiran`, async () => {
                          await api.admin.blockVoucher(t.voucher.serial)
                          await load()
                        })
                      }
                    >
                      Blokiraj bon
                    </button>
                    <button
                      className="btn"
                      disabled={checks[t.voucher.serial] === 'loading'}
                      onClick={() => checkVoucher(t.voucher.serial)}
                      title="Provjeri iskorištenost bona kod pružatelja usluge"
                    >
                      Provjeri kod pružatelja
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function CustomersTab({
  customers,
  reloadCustomers,
  run,
  report,
}: {
  customers: Customer[]
  reloadCustomers: () => Promise<void>
  run: Runner
  report: Reporter
}) {
  const [topping, setTopping] = useState<{ id: number; amount: string; note: string } | null>(null)
  const [history, setHistory] = useState<{ id: number; wallet: Wallet } | null>(null)

  const showHistory = (id: number) =>
    api.admin
      .customerWallet(id)
      .then((wallet) => setHistory({ id, wallet }))
      .catch(report)

  return (
    <section className="section">
      <h2>
        Kupci <small>walleti i podaci za kontakt</small>
      </h2>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Ime</th>
              <th>E-mail</th>
              <th>Telefon</th>
              <th className="num">Wallet</th>
              <th>Akcije</th>
            </tr>
          </thead>
          <tbody>
            {customers.map((c) => (
              <CustomerRows
                key={c.id}
                customer={c}
                topping={topping?.id === c.id ? topping : null}
                history={history?.id === c.id ? history.wallet : null}
                onStartTopUp={() => setTopping({ id: c.id, amount: '20', note: '' })}
                onChangeTopUp={(patch) => setTopping((t) => (t && t.id === c.id ? { ...t, ...patch } : t))}
                onCancelTopUp={() => setTopping(null)}
                onSubmitTopUp={() => {
                  if (!topping) return
                  const amount = Number(topping.amount)
                  run(`Uplata ${eur(amount)} na wallet kupca ${c.name}`, async () => {
                    await api.admin.topUpWallet(c.id, amount, topping.note.trim())
                    setTopping(null)
                    await reloadCustomers()
                    if (history?.id === c.id) await showHistory(c.id)
                  })
                }}
                onToggleHistory={() => (history?.id === c.id ? setHistory(null) : showHistory(c.id))}
              />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function CustomerRows({
  customer,
  topping,
  history,
  onStartTopUp,
  onChangeTopUp,
  onCancelTopUp,
  onSubmitTopUp,
  onToggleHistory,
}: {
  customer: Customer
  topping: { amount: string; note: string } | null
  history: Wallet | null
  onStartTopUp: () => void
  onChangeTopUp: (patch: { amount?: string; note?: string }) => void
  onCancelTopUp: () => void
  onSubmitTopUp: () => void
  onToggleHistory: () => void
}) {
  const amountOk = topping != null && Number(topping.amount) > 0
  return (
    <>
      <tr>
        <td>{customer.id}</td>
        <td>{customer.name}</td>
        <td>{customer.email}</td>
        <td>{customer.phone ?? '—'}</td>
        <td className="num">
          <b>{eur(customer.walletBalanceEur)}</b>
        </td>
        <td>
          {topping ? (
            <div className="resolve">
              <input
                type="number"
                min={1}
                step={1}
                autoFocus
                placeholder="Iznos (€)"
                value={topping.amount}
                onChange={(e) => onChangeTopUp({ amount: e.target.value })}
              />
              <input placeholder="Napomena (npr. goodwill)" value={topping.note} onChange={(e) => onChangeTopUp({ note: e.target.value })} />
              <div className="actions">
                <button className="btn primary" disabled={!amountOk || !topping.note.trim()} onClick={onSubmitTopUp}>
                  Uplati
                </button>
                <button className="btn" onClick={onCancelTopUp}>
                  Odustani
                </button>
              </div>
            </div>
          ) : (
            <div className="actions">
              <button className="btn" onClick={onStartTopUp}>
                Uplata
              </button>
              <button className="btn" onClick={onToggleHistory}>
                {history ? 'Sakrij povijest' : 'Povijest walleta'}
              </button>
            </div>
          )}
        </td>
      </tr>
      {history && (
        <tr className="subtable">
          <td colSpan={6}>
            {history.entries.length === 0 ? (
              <span className="muted">Nema promjena na walletu.</span>
            ) : (
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
                  {history.entries.map((e) => (
                    <tr key={e.id}>
                      <td>{dateTime(e.createdAt)}</td>
                      <td>
                        <span className={`badge ${e.type.toLowerCase()}`}>{WALLET_ENTRY[e.type]}</span>
                      </td>
                      <td className={`num ${e.amountEur < 0 ? 'neg' : 'pos'}`}>{signedEur(e.amountEur)}</td>
                      <td className="num">{eur(e.balanceAfterEur)}</td>
                      <td className="muted">
                        {e.note}
                        {e.transactionId != null && ` (transakcija #${e.transactionId})`}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </td>
        </tr>
      )}
    </>
  )
}

function StockTab({ run, report }: { run: Runner; report: Reporter }) {
  const [rows, setRows] = useState<StockRow[]>([])
  const load = useCallback(() => api.admin.stock().then(setRows), [])

  useEffect(() => {
    load().catch(report)
  }, [load, report])

  const total = (key: 'available' | 'sold' | 'redeemed' | 'blocked') => rows.reduce((sum, r) => sum + r[key], 0)

  return (
    <section className="section">
      <h2>
        Zalihe <small>bonovi po proizvodu i statusu</small>
      </h2>
      <div className="filters">
        <button className="btn" onClick={() => run('Osvježeno', load)}>
          Osvježi
        </button>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Proizvod</th>
              <th>Brend</th>
              <th className="num">Na zalihi</th>
              <th className="num">Prodano</th>
              <th className="num">Iskorišteno</th>
              <th className="num">Blokirano</th>
              <th>U prodaji</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.productId}>
                <td>{r.name}</td>
                <td>{BRAND[r.brand]}</td>
                <td className="num">{r.available === 0 ? <span className="badge blocked">0</span> : r.available}</td>
                <td className="num">{r.sold}</td>
                <td className="num">{r.redeemed}</td>
                <td className="num">{r.blocked}</td>
                <td>{r.active ? 'da' : 'ne'}</td>
              </tr>
            ))}
            {rows.length > 0 && (
              <tr>
                <th colSpan={2}>Ukupno</th>
                <th className="num">{total('available')}</th>
                <th className="num">{total('sold')}</th>
                <th className="num">{total('redeemed')}</th>
                <th className="num">{total('blocked')}</th>
                <th />
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

const EMPTY_FORM = { customerId: '', transactionId: '', subject: '', description: '' }

function TicketsTab({ customers, run, report }: { customers: Customer[]; run: Runner; report: Reporter }) {
  const [rows, setRows] = useState<Ticket[]>([])
  const [form, setForm] = useState(EMPTY_FORM)
  const [resolving, setResolving] = useState<{ id: number; text: string } | null>(null)

  const load = useCallback(() => api.admin.tickets().then(setRows), [])

  useEffect(() => {
    load().catch(report)
  }, [load, report])

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    run('Prijava otvorena', async () => {
      await api.admin.createTicket({
        customerId: Number(form.customerId),
        transactionId: form.transactionId ? Number(form.transactionId) : null,
        subject: form.subject,
        description: form.description,
      })
      setForm(EMPTY_FORM)
      await load()
    })
  }

  return (
    <section className="section">
      <h2>
        Podrška <small>prijave kupaca</small>
      </h2>

      <form className="form" onSubmit={submit}>
        <label>
          Kupac
          <select required value={form.customerId} onChange={(e) => setForm({ ...form, customerId: e.target.value })}>
            <option value="">— odaberi —</option>
            {customers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Transakcija # (opcionalno)
          <input
            type="number"
            min={1}
            value={form.transactionId}
            onChange={(e) => setForm({ ...form, transactionId: e.target.value })}
          />
        </label>
        <label className="full">
          Naslov
          <input required maxLength={200} value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} />
        </label>
        <label className="full">
          Opis
          <textarea rows={2} maxLength={2000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </label>
        <div className="submit">
          <button className="btn primary" type="submit">
            Otvori prijavu
          </button>
        </div>
      </form>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Datum</th>
              <th>Kupac</th>
              <th>Transakcija</th>
              <th>Naslov</th>
              <th>Opis</th>
              <th>Status</th>
              <th>Rješenje</th>
              <th>Akcija</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((t) => (
              <tr key={t.id}>
                <td>{t.id}</td>
                <td>{dateTime(t.createdAt)}</td>
                <td>{t.customerName}</td>
                <td>{t.transactionId ?? '—'}</td>
                <td>{t.subject}</td>
                <td className="muted">{t.description}</td>
                <td>
                  <span className={`badge ${t.status.toLowerCase()}`}>{TICKET_STATUS[t.status]}</span>
                </td>
                <td>
                  {t.resolution}
                  {t.resolvedAt && <div className="muted">{dateTime(t.resolvedAt)}</div>}
                </td>
                <td>
                  {t.status === 'OPEN' && resolving?.id !== t.id && (
                    <button className="btn" onClick={() => setResolving({ id: t.id, text: '' })}>
                      Riješi
                    </button>
                  )}
                  {resolving?.id === t.id && (
                    <div className="resolve">
                      <textarea
                        rows={2}
                        autoFocus
                        placeholder="Što je napravljeno?"
                        value={resolving.text}
                        onChange={(e) => setResolving({ id: t.id, text: e.target.value })}
                      />
                      <div className="actions">
                        <button
                          className="btn primary"
                          disabled={!resolving.text.trim()}
                          onClick={() =>
                            run(`Prijava #${t.id} riješena`, async () => {
                              await api.admin.resolveTicket(t.id, resolving.text.trim())
                              setResolving(null)
                              await load()
                            })
                          }
                        >
                          Spremi
                        </button>
                        <button className="btn" onClick={() => setResolving(null)}>
                          Odustani
                        </button>
                      </div>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
