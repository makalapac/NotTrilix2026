/**
 * Plays Nola's speech while it is still arriving (MediaSource: the first chunk starts playback) or, when the browser
 * cannot stream mp3, once the whole file is in. stop() interrupts playback (barge-in).
 */
export class VoicePlayer {
  private audio: HTMLAudioElement | null = null
  private url: string | null = null
  private generation = 0

  get playing(): boolean {
    return this.audio !== null && !this.audio.paused && !this.audio.ended
  }

  stop() {
    this.generation++
    if (this.audio) {
      this.audio.onended = null
      this.audio.pause()
      this.audio.removeAttribute('src')
      this.audio.load()
      this.audio = null
    }
    if (this.url) {
      URL.revokeObjectURL(this.url)
      this.url = null
    }
  }

  /** Reads the audio bytes from the reader and plays them; resolves with the complete mp3 when the stream ends. */
  async play(
    reader: ReadableStreamDefaultReader<Uint8Array>,
    initial: Uint8Array,
    mimeType: string,
    onFirstAudio: () => void,
    onEnded: () => void,
  ): Promise<Blob> {
    this.stop()
    const generation = this.generation
    // copies give the chunks a plain ArrayBuffer, which Blob and SourceBuffer expect
    const chunks: Uint8Array<ArrayBuffer>[] = []
    const canStream = typeof MediaSource !== 'undefined' && MediaSource.isTypeSupported(mimeType)

    if (!canStream) {
      if (initial.length) chunks.push(initial.slice())
      for (;;) {
        const { value, done } = await reader.read()
        if (done) break
        if (value?.length) chunks.push(value.slice())
      }
      const blob = new Blob(chunks, { type: mimeType })
      if (generation === this.generation) {
        onFirstAudio()
        this.playUrl(URL.createObjectURL(blob), onEnded)
      }
      return blob
    }

    const source = new MediaSource()
    const audio = new Audio()
    this.audio = audio
    this.url = URL.createObjectURL(source)
    audio.src = this.url
    audio.onended = onEnded
    await new Promise<void>((resolve) => source.addEventListener('sourceopen', () => resolve(), { once: true }))
    const buffer = source.addSourceBuffer(mimeType)
    buffer.mode = 'sequence'
    const append = (chunk: Uint8Array<ArrayBuffer>) =>
      new Promise<void>((resolve, reject) => {
        const done = () => {
          buffer.removeEventListener('updateend', done)
          buffer.removeEventListener('error', fail)
          resolve()
        }
        const fail = () => {
          buffer.removeEventListener('updateend', done)
          buffer.removeEventListener('error', fail)
          reject(new Error('audio buffer'))
        }
        buffer.addEventListener('updateend', done)
        buffer.addEventListener('error', fail)
        buffer.appendBuffer(chunk)
      })
    let started = false
    const feed = async (bytes: Uint8Array) => {
      const chunk = bytes.slice()
      chunks.push(chunk)
      if (generation !== this.generation) return // interrupted: keep draining, stop feeding
      await append(chunk)
      if (!started) {
        started = true
        onFirstAudio()
        audio.play().catch(() => undefined)
      }
    }
    try {
      if (initial.length) await feed(initial)
      for (;;) {
        const { value, done } = await reader.read()
        if (done) break
        if (value?.length) await feed(value)
      }
      if (generation === this.generation && source.readyState === 'open') source.endOfStream()
    } catch {
      // buffer error or interruption: the reply text is already on screen
    }
    return new Blob(chunks, { type: mimeType })
  }

  private playUrl(url: string, onEnded: () => void) {
    const audio = new Audio(url)
    this.audio = audio
    this.url = url
    audio.onended = onEnded
    audio.play().catch(() => undefined)
  }
}
