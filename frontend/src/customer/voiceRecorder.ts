export type StopReason = 'silence' | 'manual' | 'max' | 'no-speech'

export interface Utterance {
  blob: Blob
  mimeType: string
  reason: StopReason
}

export interface RecorderOptions {
  /** Pause after speech that ends the utterance. */
  silenceMs?: number
  /** Hard limit for one utterance. */
  maxMs?: number
  /** Give up when nothing is said at all. */
  noSpeechMs?: number
  /** RMS level (0..1) above which the signal counts as speech. */
  threshold?: number
  onLevel?: (rms: number) => void
}

function pickMimeType(): string {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4']
  return candidates.find((t) => typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(t)) ?? ''
}

export function extensionOf(mimeType: string): string {
  if (mimeType.includes('webm')) return 'webm'
  if (mimeType.includes('ogg')) return 'ogg'
  if (mimeType.includes('mp4')) return 'mp4'
  return 'webm'
}

/**
 * Records one utterance from the microphone: starts right away and stops by itself after a pause once the customer
 * has spoken (simple energy-based voice activity detection), or on stop(). No audio is kept after the promise resolves.
 */
export class VoiceRecorder {
  private recorder: MediaRecorder | null = null
  private finish: ((reason: StopReason) => void) | null = null

  get active(): boolean {
    return this.recorder !== null
  }

  async record(options: RecorderOptions = {}): Promise<Utterance> {
    const silenceMs = options.silenceMs ?? 900
    const maxMs = options.maxMs ?? 20000
    const noSpeechMs = options.noSpeechMs ?? 6000
    const threshold = options.threshold ?? 0.012

    const stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true },
    })
    const mimeType = pickMimeType()
    const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)
    this.recorder = recorder
    const chunks: Blob[] = []
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data)
    }

    const context = new AudioContext()
    const analyser = context.createAnalyser()
    analyser.fftSize = 1024
    context.createMediaStreamSource(stream).connect(analyser)
    const samples = new Float32Array(analyser.fftSize)
    const startedAt = Date.now()
    let lastSpeechAt = 0

    return new Promise<Utterance>((resolve) => {
      let reason: StopReason = 'manual'
      let timer: number | undefined
      recorder.onstop = () => {
        window.clearInterval(timer)
        stream.getTracks().forEach((t) => t.stop())
        context.close().catch(() => undefined)
        this.recorder = null
        this.finish = null
        const type = recorder.mimeType || mimeType || 'audio/webm'
        resolve({ blob: new Blob(chunks, { type }), mimeType: type, reason })
      }
      const stopWith = (why: StopReason) => {
        if (this.recorder !== recorder) return
        reason = why
        if (recorder.state !== 'inactive') recorder.stop()
      }
      this.finish = stopWith
      timer = window.setInterval(() => {
        analyser.getFloatTimeDomainData(samples)
        let sum = 0
        for (let i = 0; i < samples.length; i++) sum += samples[i] * samples[i]
        const rms = Math.sqrt(sum / samples.length)
        options.onLevel?.(rms)
        const now = Date.now()
        if (rms > threshold) lastSpeechAt = now
        if (lastSpeechAt && now - lastSpeechAt > silenceMs) stopWith('silence')
        else if (!lastSpeechAt && now - startedAt > noSpeechMs) stopWith('no-speech')
        else if (now - startedAt > maxMs) stopWith('max')
      }, 50)
      recorder.start(250)
    })
  }

  stop() {
    this.finish?.('manual')
  }
}
