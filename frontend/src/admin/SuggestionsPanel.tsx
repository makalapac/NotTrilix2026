import { useCallback, useEffect, useState } from 'react'
import { api } from '../shared/api'
import type { KbSuggestion } from '../shared/types'
import { SUGGESTION_STATUS, dateTime } from '../shared/format'

type Runner = (successText: string, action: () => Promise<unknown>) => Promise<void>
type Reporter = (e: unknown) => void

/**
 * Samoučenje s čovjekom u petlji: prijedlozi odlomaka baze znanja nastali iz analize razgovora. Admin ih čita,
 * po potrebi ispravi i odobri; tek tada ulaze u bazu znanja (odmah vrijede za agenta). Ili ih odbije.
 */
export function SuggestionsPanel({ run, report, onChanged }: { run: Runner; report: Reporter; onChanged: () => void }) {
  const [rows, setRows] = useState<KbSuggestion[]>([])
  const [edits, setEdits] = useState<Record<number, { question: string; answer: string }>>({})
  const [showDecided, setShowDecided] = useState(false)

  const load = useCallback(() => api.admin.kb.suggestions().then(setRows), [])

  useEffect(() => {
    load().catch(report)
  }, [load, report])

  const proposed = rows.filter((s) => s.status === 'PROPOSED')
  const decided = rows.filter((s) => s.status !== 'PROPOSED')

  function edit(s: KbSuggestion) {
    return edits[s.id] ?? { question: s.question, answer: s.answer }
  }

  return (
    <div className="suggestions">
      <h3>
        Naučeno iz razgovora <small>{proposed.length} prijedloga čeka odluku</small>
      </h3>
      <p className="muted">
        Analiza razgovora prepoznaje pitanja na koja baza znanja nije imala odgovor i predlaže odlomak. Nacrt odgovora je
        model, konačna riječ je tvoja: ispravi tekst pa odobri, ili odbij. Odobreno odmah ulazi u bazu znanja.
      </p>
      {proposed.length === 0 && <p className="muted">Trenutno nema prijedloga.</p>}
      {proposed.map((s) => {
        const e = edit(s)
        return (
          <article key={s.id} className="hit suggestion">
            <div className="muted">
              #{s.id} · {dateTime(s.createdAt)} · iz razgovora <span className="mono">{s.conversationId ?? '—'}</span>
            </div>
            <label>
              Pitanje kupca
              <input value={e.question} maxLength={500} onChange={(ev) => setEdits({ ...edits, [s.id]: { ...e, question: ev.target.value } })} />
            </label>
            <label>
              Odgovor (nacrt modela – provjeri i ispravi)
              <textarea rows={3} value={e.answer} maxLength={2000} onChange={(ev) => setEdits({ ...edits, [s.id]: { ...e, answer: ev.target.value } })} />
            </label>
            <div className="actions">
              <button
                className="btn primary"
                disabled={!e.question.trim() || !e.answer.trim()}
                onClick={() =>
                  run(`Prijedlog #${s.id} odobren i dodan u bazu znanja`, async () => {
                    await api.admin.kb.approveSuggestion(s.id, e.question.trim(), e.answer.trim())
                    await load()
                    onChanged()
                  })
                }
              >
                Odobri i uči
              </button>
              <button
                className="btn"
                onClick={() =>
                  run(`Prijedlog #${s.id} odbijen`, async () => {
                    await api.admin.kb.rejectSuggestion(s.id)
                    await load()
                    onChanged()
                  })
                }
              >
                Odbij
              </button>
            </div>
          </article>
        )
      })}
      {decided.length > 0 && (
        <details open={showDecided} onToggle={(ev) => setShowDecided((ev.target as HTMLDetailsElement).open)}>
          <summary className="muted">Odlučeni prijedlozi ({decided.length})</summary>
          <div className="table-wrap" style={{ marginTop: 8 }}>
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Pitanje</th>
                  <th>Odgovor</th>
                  <th>Status</th>
                  <th>Odlučio</th>
                </tr>
              </thead>
              <tbody>
                {decided.map((s) => (
                  <tr key={s.id}>
                    <td>{s.id}</td>
                    <td>{s.question}</td>
                    <td className="audit-text">{s.answer}</td>
                    <td>
                      <span className={`badge suggestion-${s.status.toLowerCase()}`}>{SUGGESTION_STATUS[s.status]}</span>
                    </td>
                    <td className="muted">
                      {s.decidedBy} · {s.decidedAt && dateTime(s.decidedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      )}
    </div>
  )
}
