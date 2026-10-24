import { useSyncExternalStore } from "react"

import { mediaUrl } from "./api"

/** Faixa de áudio tocável (mensagem de voz ou arquivo de áudio). */
export type Track = {
  mediaId: string
  /** Grant do anexo de conversa trancada (PV-03). */
  grant?: string | null
  conversationId: string
  messageId: string
  title: string
  /** Duração lida do arquivo pelo servidor (AU-02): vale enquanto o navegador não sabe (WebM gravado). */
  durationMs?: number | null
}

export type PlayerState = {
  current: Track | null
  playing: boolean
  position: number
  duration: number
  rate: number
  queue: Track[]
}

export const RATES = [1, 1.5, 2] as const

/**
 * Tocador global de áudio (AU-02): um único elemento de áudio para o app inteiro, então o som continua ao
 * trocar de conversa; ao terminar uma faixa, toca a próxima da fila (áudios seguintes da mesma conversa). A
 * velocidade escolhida vale para as próximas faixas.
 */
class AudioPlayer {
  private element: HTMLAudioElement | null = null
  private listeners = new Set<() => void>()
  private state: PlayerState = {
    current: null,
    playing: false,
    position: 0,
    duration: 0,
    rate: 1,
    queue: [],
  }

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => {
      this.listeners.delete(listener)
    }
  }

  snapshot = () => this.state

  private set(next: Partial<PlayerState>) {
    this.state = { ...this.state, ...next }
    this.listeners.forEach((listener) => listener())
  }

  private audio(): HTMLAudioElement {
    if (!this.element) {
      const element = new Audio()
      element.preload = "metadata"
      element.addEventListener("timeupdate", () =>
        this.set({ position: element.currentTime })
      )
      // O WebM do MediaRecorder chega sem duração (Infinity): vale a que o servidor leu do arquivo.
      element.addEventListener("durationchange", () =>
        this.set({
          duration: Number.isFinite(element.duration)
            ? element.duration
            : knownDuration(this.state.current),
        })
      )
      element.addEventListener("play", () => this.set({ playing: true }))
      element.addEventListener("pause", () => this.set({ playing: false }))
      element.addEventListener("ended", () => this.next())
      this.element = element
    }
    return this.element
  }

  /** Toca a faixa; `queue` é a lista da conversa em ordem, para seguir para os próximos áudios. */
  play(track: Track, queue: Track[] = [track]) {
    const audio = this.audio()
    if (this.state.current?.mediaId === track.mediaId) {
      void audio.play().catch(() => undefined)
      return
    }
    audio.src = mediaUrl({ id: track.mediaId, grant: track.grant })
    audio.playbackRate = this.state.rate
    this.set({
      current: track,
      queue,
      position: 0,
      duration: knownDuration(track),
    })
    void audio.play().catch(() => this.set({ playing: false }))
  }

  pause() {
    this.element?.pause()
  }

  toggle(track: Track, queue?: Track[]) {
    if (this.state.current?.mediaId === track.mediaId && this.state.playing) {
      this.pause()
    } else {
      this.play(track, queue)
    }
  }

  seek(seconds: number) {
    if (this.element) {
      this.element.currentTime = seconds
      this.set({ position: seconds })
    }
  }

  /** Alterna 1× → 1,5× → 2× → 1×. */
  cycleRate() {
    const index = RATES.indexOf(this.state.rate as (typeof RATES)[number])
    const rate = RATES[(index + 1) % RATES.length]
    if (this.element) {
      this.element.playbackRate = rate
    }
    this.set({ rate })
  }

  stop() {
    if (this.element) {
      this.element.pause()
      this.element.removeAttribute("src")
      this.element.load()
    }
    this.set({ current: null, playing: false, position: 0, duration: 0 })
  }

  private next() {
    const { current, queue } = this.state
    const index = queue.findIndex((item) => item.mediaId === current?.mediaId)
    const following = index >= 0 ? queue[index + 1] : undefined
    if (following) {
      this.play(following, queue)
    } else {
      this.set({ playing: false, position: 0 })
    }
  }
}

/** Duração em segundos que o servidor leu do arquivo; 0 quando não se sabe. */
function knownDuration(track: Track | null): number {
  return (track?.durationMs ?? 0) / 1000
}

export const player = new AudioPlayer()

export function usePlayer(): PlayerState {
  return useSyncExternalStore(player.subscribe, player.snapshot)
}

export function formatDuration(seconds: number): string {
  const total = Math.max(0, Math.floor(seconds))
  const minutes = Math.floor(total / 60)
  return `${minutes}:${String(total % 60).padStart(2, "0")}`
}

/** "1×", "1,5×" e "2×" no idioma da interface. */
export function formatRate(rate: number, locale: string): string {
  return `${new Intl.NumberFormat(locale).format(rate)}×`
}
