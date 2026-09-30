import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { api, isUnauthorized } from '../shared/api'
import type { AgentChatResponse, AuthUser } from '../shared/types'
import { AGENT_OUTCOME, TOOL_LABEL, errorMessage } from '../shared/format'

interface ChatMessage {
  role: 'user' | 'agent' | 'error'
  text: string
  response?: AgentChatResponse
  /** voice turn: the message is a transcript, the reply has speech */
  voice?: boolean
  audioUrl?: string
}

const SUGGESTIONS = ['Koliko imam na walletu?', 'Moj kod ne radi', 'Mogu li vratiti zadnji bon?', 'Što čuvate o meni?']

/** Voice consent text; the version must match nottrilix.privacy.voice-consent-version in the system. */
export const VOICE_CONSENT_TEXT =
  'Glasovnu poruku pretvara u tekst OpenAI, a odgovor u govor ElevenLabs (izvršitelji obrade). Snimka se ne pohranjuje: ' +
  'čuva se samo transkript, kao i poruke u chatu, 30 dana. Glas se ne koristi za prepoznavanje identiteta. ' +
  'Privolu možete povući u odjeljku Privatnost.'

function pickMimeType(): string {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4']
  return candidates.find((t) => typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(t)) ?? ''
}

function extensionOf(mimeType: string): string {
  if (mimeType.includes('webm')) return 'webm'
  if (mimeType.includes('ogg')) return 'ogg'
  if (mimeType.includes('mp4')) return 'mp4'
  return 'webm'
}

/**
 * Chat with the support agent, by text or voice. The agent acts on behalf of the logged-in customer; under every reply you
 * can see what it did (tools) and what it relied on (knowledge base sections). Voice: hold the button and speak; the recording
 * goes to the system, which turns it into text and returns the reply as speech. The first time it asks for the customer's consent (GDPR).
 */
export function ChatWidget({
  user,
  voiceConsent,
  onVoiceConsent,
  onStateChanged,
  onSessionExpired,
}: {
  user: AuthUser
  voiceConsent: boolean
  onVoiceConsent: () => Promise<void>
  onStateChanged: () => void
  onSessionExpired: () => void
}) {
  const [open, setOpen] = useState(false)
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [recording, setRecording] = useState(false)
  const [askConsent, setAskConsent] = useState(false)
  const [voiceError, setVoiceError] = useState<string | null>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])

  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [messages, busy, open])

  function handleResponse(response: AgentChatResponse, extra: Partial<ChatMessage> = {}) {
    setConversationId(response.conversationId)
    setMessages((m) => [...m, { role: 'agent', text: response.reply, response, ...extra }])
    if (response.toolCalls.length > 0) onStateChanged()
  }

  function handleError(e: unknown) {
    if (isUnauthorized(e)) onSessionExpired()
    else setMessages((m) => [...m, { role: 'error', text: errorMessage(e) }])
  }

  async function send(message: string) {
    const trimmed = message.trim()
    if (!trimmed || busy) return
    setMessages((m) => [...m, { role: 'user', text: trimmed }])
    setText('')
    setBusy(true)
    try {
      handleResponse(await api.agentChat(conversationId, trimmed))
    } catch (e) {
      handleError(e)
    } finally {
      setBusy(false)
    }
  }

  async function sendVoice(blob: Blob, mimeType: string) {
    setBusy(true)
    setMessages((m) => [...m, { role: 'user', text: '🎤 …', voice: true }])
    try {
      const turn = await api.agentVoice(conversationId, blob, `speech.${extensionOf(mimeType)}`)
      // the transcript replaces the temporary "…" in the customer's message
      setMessages((m) => {
        const copy = [...m]
        const last = copy.length - 1
        if (last >= 0 && copy[last].role === 'user' && copy[last].voice) copy[last] = { ...copy[last], text: turn.transcript }
        return copy
      })
      const audioUrl = `data:${turn.audioMimeType};base64,${turn.audioBase64}`
      handleResponse(turn.chat, { voice: true, audioUrl })
      new Audio(audioUrl).play().catch(() => {
        /* autoplay may be blocked; the ▶ button remains */
      })
    } catch (e) {
      setMessages((m) => m.filter((x) => !(x.role === 'user' && x.voice && x.text === '🎤 …')))
      handleError(e)
    } finally {
      setBusy(false)
    }
  }

  async function startRecording() {
    if (busy || recording) return
    if (!voiceConsent) {
      setAskConsent(true)
      return
    }
    setVoiceError(null)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const mimeType = pickMimeType()
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)
      chunksRef.current = []
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data)
      }
      recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop())
        const type = recorder.mimeType || mimeType || 'audio/webm'
        const blob = new Blob(chunksRef.current, { type })
        if (blob.size > 2000) sendVoice(blob, type)
        else setVoiceError('Snimka je prekratka. Držite gumb dok govorite.')
      }
      recorderRef.current = recorder
      recorder.start()
      setRecording(true)
    } catch (e) {
      setVoiceError(`Mikrofon nije dostupan: ${errorMessage(e)}`)
    }
  }

  function stopRecording() {
    const recorder = recorderRef.current
    if (recorder && recorder.state !== 'inactive') recorder.stop()
    recorderRef.current = null
    setRecording(false)
  }

  async function acceptConsent() {
    try {
      await onVoiceConsent()
      setAskConsent(false)
    } catch (e) {
      handleError(e)
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

  // the server (ConfirmationGate) knows what awaits confirmation; we do not guess from the reply text
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
          <div className="privacy-note">
            Razgovor obrađuje AI (OpenAI) i čuva se 30 dana. Detalji i vaša prava: Privatnost.
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
            <div className="bubble">
              {m.voice && m.role === 'user' && <span className="voice-mark">🎤 </span>}
              {m.text}
            </div>
            {m.audioUrl && (
              <button className="btn ghost replay" onClick={() => new Audio(m.audioUrl).play().catch(() => undefined)}>
                ▶ ponovi
              </button>
            )}
            {m.response && <AgentActivity response={m.response} />}
          </div>
        ))}
        {busy && (
          <div className="msg agent">
            <div className="bubble muted">Nola {last?.voice ? 'sluša i odgovara' : 'piše'}…</div>
          </div>
        )}
      </div>

      {askConsent && (
        <div className="consent-box">
          <b>Privola za glasovni razgovor</b>
          <p>{VOICE_CONSENT_TEXT}</p>
          <div className="actions">
            <button className="btn primary" onClick={acceptConsent}>
              Slažem se
            </button>
            <button className="btn" onClick={() => setAskConsent(false)}>
              Ne sada
            </button>
          </div>
        </div>
      )}

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

      {voiceError && <div className="voice-error">{voiceError}</div>}

      <form className="chat-input" onSubmit={submit}>
        <button
          type="button"
          className={`btn mic ${recording ? 'recording' : ''}`}
          title={voiceConsent ? 'Držite i govorite' : 'Glasovna poruka (traži privolu)'}
          disabled={busy}
          onMouseDown={startRecording}
          onMouseUp={stopRecording}
          onMouseLeave={() => recording && stopRecording()}
          onTouchStart={(e) => {
            e.preventDefault()
            startRecording()
          }}
          onTouchEnd={(e) => {
            e.preventDefault()
            stopRecording()
          }}
        >
          {recording ? '● snimam' : '🎤'}
        </button>
        <input
          value={text}
          placeholder={recording ? 'Govorite…' : 'Napišite poruku ili držite 🎤 i govorite'}
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

/** What the agent did in this turn: tools with their outcome and knowledge base sections. */
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
