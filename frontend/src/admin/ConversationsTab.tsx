import { useCallback, useEffect, useState } from 'react'
import { api, isUnauthorized } from '../shared/api'
import type { Analysis, Conversation, ConversationDetail, ConversationStats } from '../shared/types'
import {
  ANALYSIS_CATEGORY,
  ANALYSIS_OUTCOME,
  CONVERSATION_STATUS,
  SENTIMENT,
  dateTime,
  errorMessage,
} from '../shared/format'

type Runner = (successText: string, action: () => Promise<unknown>) => Promise<void>
type Reporter = (e: unknown) => void

/**
 * Razgovori kupaca s agentom i post-analiza: brojke, popis s nalazom, transkript iz audit traga.
 * Analizu radi model nad pseudonimiziranim transkriptom; ovdje admin vidi izvorni transkript (ovlašteno osoblje).
 */
export function ConversationsTab({ run, report }: { run: Runner; report: Reporter }) {
  const [stats, setStats] = useState<ConversationStats | null>(null)
  const [rows, setRows] = useState<Conversation[]>([])
  const [selected, setSelected] = useState<ConversationDetail | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(
    () => Promise.all([api.admin.conversations.stats().then(setStats), api.admin.conversations.list().then(setRows)]),
    [],
  )

  useEffect(() => {
    load().catch(report)
  }, [load, report])

  async function openDetail(id: string) {
    try {
      setSelected(await api.admin.conversations.detail(id))
    } catch (e) {
      report(e)
    }
  }

  async function analyze(id: string) {
    setBusyId(id)
    try {
      await api.admin.conversations.analyze(id)
      await load()
      if (selected?.conversation.id === id) await openDetail(id)
    } catch (e) {
      if (isUnauthorized(e)) report(e)
      else report(new Error(errorMessage(e)))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="section">
      <h2>
        Razgovori i analiza <small>{rows.length} razgovora, najnoviji prvi</small>
      </h2>

      {stats && (
        <div className="stat-grid">
          <Stat label="Razgovora" value={stats.conversations} hint={`${stats.open} otvoreno · ${stats.analyzed} analizirano · ${stats.erased} obrisano`} />
          <Stat label="Prosječna ocjena agenta" value={stats.averageQuality == null ? '—' : stats.averageQuality.toFixed(1) + ' / 5'} />
          <Stat label="Teme" value={distribution(stats.byCategory, ANALYSIS_CATEGORY)} />
          <Stat label="Ishodi" value={distribution(stats.byOutcome, ANALYSIS_OUTCOME)} />
          <Stat label="Raspoloženje" value={distribution(stats.bySentiment, SENTIMENT)} />
          <Stat label="Prijedlozi za bazu znanja" value={`${stats.proposedSuggestions} čeka · ${stats.approvedSuggestions} odobreno`} hint="tab Znanje" />
        </div>
      )}

      <div className="filters">
        <button className="btn" onClick={() => run('Osvježeno', load)}>
          Osvježi
        </button>
        <button
          className="btn"
          onClick={() =>
            run('Analizirani utihnuli razgovori', async () => {
              const done = await api.admin.conversations.analyzeIdle()
              await load()
              if (done.length === 0) throw new Error('Nema utihnulih razgovora za analizu (idle 2 min).')
            })
          }
        >
          Analiziraj utihnule
        </button>
        <span className="muted">Raspored svake minute analizira razgovore bez nove poruke dulje od 2 min.</span>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Razgovor</th>
              <th>Kupac</th>
              <th>Kanal</th>
              <th>Zadnja poruka</th>
              <th className="num">Turnova</th>
              <th>Status</th>
              <th>Tema</th>
              <th>Ishod</th>
              <th>Raspoloženje</th>
              <th className="num">Ocjena</th>
              <th>Sažetak</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((c) => (
              <tr key={c.id} className={selected?.conversation.id === c.id ? 'selected' : ''}>
                <td className="mono">
                  <button className="link" onClick={() => openDetail(c.id)}>
                    {c.id}
                  </button>
                </td>
                <td>{c.customerName ?? <span className="muted">anonimno</span>}</td>
                <td>{c.channel}</td>
                <td>{dateTime(c.lastTurnAt)}</td>
                <td className="num">{c.turns}</td>
                <td>
                  <span className={`badge status-${c.status.toLowerCase()}`}>{CONVERSATION_STATUS[c.status]}</span>
                </td>
                <td>{c.analysis ? ANALYSIS_CATEGORY[c.analysis.category] : '—'}</td>
                <td>
                  {c.analysis ? (
                    <span className={`badge outcome-${c.analysis.outcome.toLowerCase()}`}>{ANALYSIS_OUTCOME[c.analysis.outcome]}</span>
                  ) : (
                    '—'
                  )}
                </td>
                <td>
                  {c.analysis ? (
                    <span className={`badge sentiment-${c.analysis.sentiment.toLowerCase()}`}>{SENTIMENT[c.analysis.sentiment]}</span>
                  ) : (
                    '—'
                  )}
                </td>
                <td className="num">{c.analysis ? c.analysis.qualityScore : '—'}</td>
                <td className="audit-text">{c.analysis?.summary ?? ''}</td>
                <td>
                  {c.status !== 'ERASED' && c.turns > 0 && (
                    <button className="btn" disabled={busyId === c.id} onClick={() => analyze(c.id)}>
                      {busyId === c.id ? 'Analiziram…' : c.analysis ? 'Ponovi' : 'Analiziraj'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {selected && (
        <div className="detail-panel">
          <div className="detail-head">
            <h3>
              Razgovor <span className="mono">{selected.conversation.id}</span>
              {selected.conversation.customerName && ` · ${selected.conversation.customerName}`} · {selected.conversation.channel}
            </h3>
            <button className="btn ghost" onClick={() => setSelected(null)}>
              Zatvori
            </button>
          </div>
          {selected.conversation.analysis && <AnalysisCard analysis={selected.conversation.analysis} />}
          <div className="transcript">
            {selected.transcript.length === 0 && <p className="muted">Poruke su obrisane (zahtjev kupca ili istek roka).</p>}
            {selected.transcript.map((line, i) => (
              <div key={i} className={`tline role-${line.role.toLowerCase()}`}>
                <span className="turn">{line.turn}</span>
                <span className="role">{line.role}</span>
                <span className="text">{line.text}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}

function AnalysisCard({ analysis }: { analysis: Analysis }) {
  return (
    <div className="analysis-card">
      <div className="analysis-badges">
        <span className="badge">{ANALYSIS_CATEGORY[analysis.category]}</span>
        <span className={`badge outcome-${analysis.outcome.toLowerCase()}`}>{ANALYSIS_OUTCOME[analysis.outcome]}</span>
        <span className={`badge sentiment-${analysis.sentiment.toLowerCase()}`}>{SENTIMENT[analysis.sentiment]}</span>
        <span className="badge">ocjena {analysis.qualityScore} / 5</span>
        <span className={`badge ${analysis.rulesFollowed ? 'outcome-ok' : 'outcome-denied'}`}>
          {analysis.rulesFollowed ? 'pravila poštovana' : 'pravila prekršena'}
        </span>
        <span className="muted">
          {analysis.model} · {analysis.turns} turnova · {analysis.toolCalls} alata · {dateTime(analysis.createdAt)}
        </span>
      </div>
      <p>{analysis.summary}</p>
      {analysis.knowledgeGaps.length > 0 && (
        <div>
          <b>Rupe u znanju:</b>
          <ul>
            {analysis.knowledgeGaps.map((g, i) => (
              <li key={i}>{g}</li>
            ))}
          </ul>
        </div>
      )}
      {analysis.improvementNotes && (
        <p className="muted">
          <b>Za poboljšati:</b> {analysis.improvementNotes}
        </p>
      )}
    </div>
  )
}

function Stat({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <div className="stat">
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint muted">{hint}</div>}
    </div>
  )
}

function distribution(counts: Record<string, number>, labels: Record<string, string>): string {
  const entries = Object.entries(counts).sort((a, b) => b[1] - a[1])
  if (entries.length === 0) return '—'
  return entries.map(([k, v]) => `${labels[k] ?? k} ${v}`).join(' · ')
}
