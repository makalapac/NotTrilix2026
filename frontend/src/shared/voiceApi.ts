import { ApiError, getToken } from './api'
import type { AgentChatResponse } from './types'

/**
 * A streamed voice turn (POST /api/agent/voice/stream): the system forwards the agent's JSON event lines
 * ({"type":"transcript"}, {"type":"chat"}, {"type":"audio"}) and then the raw mp3 bytes as they are synthesized.
 */
export interface VoiceStreamHandlers {
  onTranscript: (transcript: string, sttMs: number) => void
  onChat: (chat: AgentChatResponse, ttsProvider: string) => void
  /** Called once, with the reader positioned at the audio bytes; `initial` holds bytes that arrived together with the header. */
  onAudio: (reader: ReadableStreamDefaultReader<Uint8Array>, initial: Uint8Array, mimeType: string) => Promise<void>
}

function authHeaders(): Record<string, string> {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function concat(a: Uint8Array, b: Uint8Array): Uint8Array {
  if (a.length === 0) return b
  const out = new Uint8Array(a.length + b.length)
  out.set(a, 0)
  out.set(b, a.length)
  return out
}

export async function voiceStream(
  conversationId: string | null,
  audio: Blob,
  filename: string,
  handlers: VoiceStreamHandlers,
): Promise<void> {
  const form = new FormData()
  form.append('audio', audio, filename)
  if (conversationId) form.append('conversationId', conversationId)
  const res = await fetch('/api/agent/voice/stream', { method: 'POST', body: form, headers: authHeaders() })
  if (!res.ok) {
    const text = await res.text()
    let body: { error?: string; message?: string } | null = null
    try {
      body = text ? JSON.parse(text) : null
    } catch {
      body = null
    }
    throw new ApiError(res.status, body?.error ?? `HTTP_${res.status}`, body?.message ?? `HTTP ${res.status}`)
  }
  if (!res.body) throw new ApiError(res.status, 'NO_STREAM', 'Preglednik ne podržava streaming odgovora')

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let pending: Uint8Array = new Uint8Array(0)
  for (;;) {
    const newline = pending.indexOf(10)
    if (newline >= 0) {
      const line = decoder.decode(pending.subarray(0, newline))
      pending = pending.subarray(newline + 1)
      const event = JSON.parse(line) as { type: string; [key: string]: unknown }
      switch (event.type) {
        case 'transcript':
          handlers.onTranscript(String(event.transcript ?? ''), Number(event.sttMs ?? 0))
          break
        case 'chat':
          handlers.onChat(event.chat as AgentChatResponse, String(event.ttsProvider ?? ''))
          break
        case 'error':
          throw new ApiError(503, String(event.error ?? 'AGENT_ERROR'), String(event.message ?? 'Greška'))
        case 'audio':
          await handlers.onAudio(reader, pending, String(event.mimeType ?? 'audio/mpeg'))
          return
        default:
          break
      }
      continue
    }
    const { value, done } = await reader.read()
    if (done) return
    if (value) pending = concat(pending, value)
  }
}

let fillerUrl: string | null = null

/** Nola's bridging phrase ("Samo trenutak.") as an object URL, fetched once per page; null when voice is unavailable. */
export async function voiceFillerUrl(): Promise<string | null> {
  if (fillerUrl) return fillerUrl
  try {
    const res = await fetch('/api/agent/voice/filler', { headers: authHeaders() })
    if (!res.ok) return null
    fillerUrl = URL.createObjectURL(await res.blob())
    return fillerUrl
  } catch {
    return null
  }
}
