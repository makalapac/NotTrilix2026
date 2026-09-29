import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { api, isUnauthorized } from '../shared/api'
import type { AgentChatResponse, AuthUser } from '../shared/types'
import { AGENT_OUTCOME, TOOL_LABEL, errorMessage } from '../shared/format'

interface ChatMessage {
  role: 'user' | 'agent' | 'error'
  text: string
  response?: AgentChatResponse
}

const SUGGESTIONS = ['Koliko imam na walletu?', 'Moj kod ne radi', 'Mogu li vratiti zadnji bon?', 'Koliki je dnevni limit?']

/**
 * Chat s agentom podrške. Agent radi u ime prijavljenog kupca; ispod svakog odgovora vidi se što je napravio
 * (alati) i na što se oslonio (odlomci baze znanja). Nakon odgovora shop osvježi wallet, kupnje i katalog.
 */
export function ChatWidget({
  user,
  onStateChanged,
  onSessionExpired,
}: {
  user: AuthUser
  onStateChanged: () => void
  onSessionExpired: () => void
}) {
  const [open, setOpen] = useState(false)
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [messages, busy, open])

  async function send(message: string) {
    const trimmed = message.trim()
    if (!trimmed || busy) return
    setMessages((m) => [...m, { role: 'user', text: trimmed }])
    setText('')
    setBusy(true)
    try {
      const response = await api.agentChat(conversationId, trimmed)
      setConversationId(response.conversationId)
      setMessages((m) => [...m, { role: 'agent', text: response.reply, response }])
      if (response.toolCalls.length > 0) onStateChanged()
    } catch (e) {
      if (isUnauthorized(e)) onSessionExpired()
      else setMessages((m) => [...m, { role: 'error', text: errorMessage(e) }])
    } finally {
      setBusy(false)
    }
  }

  function newConversation() {
    setConversationId(null)
    setMessages([])
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    send(text)
  }

  // što čeka potvrdu zna server (ConfirmationGate), ne nagađamo iz teksta odgovora
  const last = messages[messages.length - 1]
  const pendingAction = !busy && last?.role === 'agent' ? (last.response?.pendingAction ?? null) : null

  if (!open) {
    return (
      <button className="chat-fab" onClick={() => setOpen(true)}>
        Chat s podrškom
      </button>
    )
  }

  return (
    <aside className="chat-panel" aria-label="Chat s podrškom">
      <header className="chat-header">
        <div>
          <b>Nola</b> · NotTrilix podrška
          {conversationId && <div className="chat-conv">razgovor {conversationId}</div>}
        </div>
        <div className="actions">
          <button className="btn ghost" onClick={newConversation} disabled={busy}>
            Novi
          </button>
          <button className="btn ghost" onClick={() => setOpen(false)}>
            Zatvori
          </button>
        </div>
      </header>

      <div className="chat-messages" ref={listRef}>
        <div className="msg agent">
          <div className="bubble">
            Bok, {user.name.split(' ')[0]}! Ja sam Nola, virtualna agentica NotTrilix podrške. Kako vam mogu pomoći?
          </div>
        </div>
        {messages.length === 0 && (
          <div className="chips">
            {SUGGESTIONS.map((s) => (
              <button key={s} type="button" onClick={() => send(s)}>
                {s}
              </button>
            ))}
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`msg ${m.role}`}>
            <div className="bubble">{m.text}</div>
            {m.response && <AgentActivity response={m.response} />}
          </div>
        ))}
        {busy && (
          <div className="msg agent">
            <div className="bubble muted">Nola piše…</div>
          </div>
        )}
      </div>

      {pendingAction && (
        <div className="quick-replies">
          <div className="pending-action">Čeka vašu potvrdu: {pendingAction}</div>
          <button className="btn primary" onClick={() => send('Da, potvrđujem.')}>
            Da, potvrđujem
          </button>
          <button className="btn" onClick={() => send('Ne, odustajem.')}>
            Ne, odustajem
          </button>
        </div>
      )}

      <form className="chat-input" onSubmit={submit}>
        <input
          value={text}
          placeholder="Napišite poruku…"
          maxLength={2000}
          onChange={(e) => setText(e.target.value)}
          autoFocus
        />
        <button className="btn primary" type="submit" disabled={busy || !text.trim()}>
          Pošalji
        </button>
      </form>
    </aside>
  )
}

/** Što je agent napravio u ovom turnu: alati s ishodom i odlomci baze znanja. */
function AgentActivity({ response }: { response: AgentChatResponse }) {
  if (response.toolCalls.length === 0 && response.sources.length === 0) return null
  return (
    <details className="activity">
      <summary>
        {response.toolCalls.length} alata · {response.sources.length} izvora · {(response.durationMs / 1000).toFixed(1)} s
      </summary>
      {response.toolCalls.map((t, i) => (
        <div key={`t${i}`} className="activity-row">
          <span className={`badge outcome-${t.outcome.toLowerCase()}`}>{AGENT_OUTCOME[t.outcome] ?? t.outcome}</span>{' '}
          {TOOL_LABEL[t.tool] ?? t.tool} <span className="muted mono">{t.input === '{}' ? '' : t.input}</span>
        </div>
      ))}
      {response.sources.map((s, i) => (
        <div key={`s${i}`} className="activity-row muted">
          izvor: {s.title} › {s.section} ({(s.score * 100).toFixed(0)} %)
        </div>
      ))}
    </details>
  )
}
