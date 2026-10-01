import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { api, isUnauthorized } from '../shared/api'
import { voiceFillerUrl, voiceStream } from '../shared/voiceApi'
import type { AgentChatResponse, AuthUser } from '../shared/types'
import { AGENT_OUTCOME, TOOL_LABEL, errorMessage } from '../shared/format'
import { VoicePlayer } from './voicePlayer'
import { VoiceRecorder, extensionOf } from './voiceRecorder'

interface ChatMessage {
  role: 'user' | 'agent' | 'error'
  text: string
  response?: AgentChatResponse
  /** voice turn: the message is a transcript, the reply has speech */
  voice?: boolean
  audioUrl?: string
}

const SUGGESTIONS = ['Koliko imam na walletu?', 'Moj kod ne radi', 'Mogu li vratiti zadnji bon?', 'Što čuvate o meni?']
const LISTENING_PLACEHOLDER = '🎤 …'
/** How long Nola stays silent after the transcript before she bridges the wait with "Samo trenutak." */
const FILLER_AFTER_MS = 900

/** Voice consent text; the version must match nottrilix.privacy.voice-consent-version in the system. */
export const VOICE_CONSENT_TEXT =
  'Glasovnu poruku pretvara u tekst OpenAI, a odgovor u govor ElevenLabs (izvršitelji obrade). Snimka se ne pohranjuje: ' +
  'čuva se samo transkript, kao i poruke u chatu, 30 dana. Glas se ne koristi za prepoznavanje identiteta. ' +
  'Privolu možete povući u odjeljku Privatnost.'

/**
 * Chat with the support agent, by text or voice. The agent acts on behalf of the logged-in customer; under every reply you
 * can see what it did (tools) and what it relied on (knowledge base sections). Voice: click the microphone and speak; the
 * recording stops by itself after a pause, the transcript shows up as soon as it is known, and Nola's reply is played while
 * it is still being synthesized. Clicking the microphone while she speaks interrupts her. The first time it asks for consent (GDPR).
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
  const [level, setLevel] = useState(0)
  const [speaking, setSpeaking] = useState(false)
  const [askConsent, setAskConsent] = useState(false)
  const [voiceError, setVoiceError] = useState<string | null>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const recorderRef = useRef(new VoiceRecorder())
  const playerRef = useRef(new VoicePlayer())
  const fillerRef = useRef<HTMLAudioElement | null>(null)

  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [messages, busy, open])

  // Nola's bridging phrase is fetched once, so it can start without any delay
  useEffect(() => {
    if (!voiceConsent || fillerRef.current) return
    voiceFillerUrl().then((url) => {
      if (url) fillerRef.current = new Audio(url)
    })
  }, [voiceConsent])

  useEffect(() => {
    const player = playerRef.current
    return () => player.stop()
  }, [])

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
    stopSpeaking()
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

  function stopSpeaking() {
    playerRef.current.stop()
    const filler = fillerRef.current
    if (filler) {
      filler.pause()
      filler.currentTime = 0
    }
    setSpeaking(false)
  }

  /** Attaches the finished mp3 to the last agent message, for the ▶ button. */
  function attachAudio(blob: Blob) {
    const url = URL.createObjectURL(blob)
    setMessages((m) => {
      const copy = [...m]
      for (let i = copy.length - 1; i >= 0; i--) {
        if (copy[i].role === 'agent') {
          copy[i] = { ...copy[i], audioUrl: url }
          break
        }
      }
      return copy
    })
  }

  async function sendVoice(blob: Blob, mimeType: string) {
    setBusy(true)
    setMessages((m) => [...m, { role: 'user', text: LISTENING_PLACEHOLDER, voice: true }])
    let fillerTimer: number | undefined
    let audioStarted = false
    try {
      await voiceStream(conversationId, blob, `speech.${extensionOf(mimeType)}`, {
        onTranscript: (transcript) => {
          setMessages((m) => {
            const copy = [...m]
            const last = copy.length - 1
            if (last >= 0 && copy[last].role === 'user' && copy[last].voice) copy[last] = { ...copy[last], text: transcript }
            return copy
          })
          // if the reply takes a while, Nola says "Samo trenutak." instead of staying silent
          fillerTimer = window.setTimeout(() => {
            if (!audioStarted) fillerRef.current?.play().catch(() => undefined)
          }, FILLER_AFTER_MS)
        },
        onChat: (chat) => {
          handleResponse(chat, { voice: true })
          setBusy(false)
        },
        onAudio: async (reader, initial, mime) => {
          setSpeaking(true)
          const full = await playerRef.current.play(
            reader,
            initial,
            mime,
            () => {
              audioStarted = true
              window.clearTimeout(fillerTimer)
              const filler = fillerRef.current
              if (filler) {
                filler.pause()
                filler.currentTime = 0
              }
            },
            () => setSpeaking(false),
          )
          attachAudio(full)
        },
      })
    } catch (e) {
      setMessages((m) => m.filter((x) => !(x.role === 'user' && x.voice && x.text === LISTENING_PLACEHOLDER)))
      handleError(e)
      setSpeaking(false)
    } finally {
      window.clearTimeout(fillerTimer)
      setBusy(false)
    }
  }

  /** One click: start listening (and interrupt Nola if she is speaking); a second click ends the utterance early. */
  async function toggleRecording() {
    if (recording) {
      recorderRef.current.stop()
      return
    }
    if (busy) return
    if (!voiceConsent) {
      setAskConsent(true)
      return
    }
    setVoiceError(null)
    stopSpeaking()
    setRecording(true)
    try {
      const utterance = await recorderRef.current.record({ onLevel: setLevel })
      setRecording(false)
      setLevel(0)
      if (utterance.reason === 'no-speech') {
        setVoiceError('Nisam čula ništa. Kliknite 🎤 i govorite.')
      } else if (utterance.blob.size < 2000) {
        setVoiceError('Snimka je prekratka. Kliknite 🎤 i govorite.')
      } else {
        await sendVoice(utterance.blob, utterance.mimeType)
      }
    } catch (e) {
      setRecording(false)
      setLevel(0)
      setVoiceError(`Mikrofon nije dostupan: ${errorMessage(e)}`)
    }
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
    stopSpeaking()
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

  const micTitle = recording
    ? 'Slušam… kliknite za kraj'
    : speaking
      ? 'Prekinite Nolu i govorite'
      : voiceConsent
        ? 'Kliknite i govorite; snimanje staje kad zašutite'
        : 'Glasovna poruka (traži privolu)'

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
              <button
                className="btn ghost replay"
                onClick={() => {
                  stopSpeaking()
                  new Audio(m.audioUrl).play().catch(() => undefined)
                }}
              >
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
          className={`btn mic ${recording ? 'recording' : ''} ${speaking ? 'speaking' : ''}`}
          title={micTitle}
          disabled={busy && !recording}
          onClick={toggleRecording}
          style={recording ? { boxShadow: `0 0 0 ${Math.min(10, Math.round(level * 80))}px rgba(220, 38, 38, 0.25)` } : undefined}
        >
          {recording ? '● slušam' : speaking ? '🔊' : '🎤'}
        </button>
        <input
          value={text}
          placeholder={recording ? 'Govorite… snimanje staje kad zašutite' : 'Napišite poruku ili kliknite 🎤 i govorite'}
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
