import { useEffect, useMemo, useState, type RefObject } from "react"

import { RATES } from "./player"

export type MediaElementState = {
  playing: boolean
  position: number
  duration: number
  rate: number
  muted: boolean
}

const EVENTS = [
  "play",
  "pause",
  "ended",
  "timeupdate",
  "durationchange",
  "loadedmetadata",
  "ratechange",
  "volumechange",
  "emptied",
] as const

/**
 * Estado e ações de um <audio>/<video> próprio da tela (sem os controles nativos, UI-05). O tocador global das
 * mensagens de voz fica em player.ts; este hook é para pré-escuta, visualização única e vídeos.
 */
export function useMediaElement(ref: RefObject<HTMLMediaElement | null>) {
  const [state, setState] = useState<MediaElementState>({
    playing: false,
    position: 0,
    duration: 0,
    rate: 1,
    muted: false,
  })

  useEffect(() => {
    const element = ref.current
    if (!element) {
      return
    }
    const sync = () =>
      setState({
        playing: !element.paused && !element.ended,
        position: element.currentTime,
        duration: Number.isFinite(element.duration) ? element.duration : 0,
        rate: element.playbackRate,
        muted: element.muted,
      })
    // WebM gravado pelo MediaRecorder chega sem duração no Chromium (Infinity): ir até o fim faz o navegador
    // calcular a duração; depois volta ao início.
    const measure = () => {
      if (element.duration !== Infinity) {
        return
      }
      const back = () => {
        element.removeEventListener("durationchange", back)
        element.currentTime = 0
      }
      element.addEventListener("durationchange", back)
      element.currentTime = Number.MAX_SAFE_INTEGER
    }
    EVENTS.forEach((name) => element.addEventListener(name, sync))
    element.addEventListener("loadedmetadata", measure)
    sync()
    return () => {
      EVENTS.forEach((name) => element.removeEventListener(name, sync))
      element.removeEventListener("loadedmetadata", measure)
    }
  }, [ref])

  const actions = useMemo(
    () => ({
      toggle() {
        const element = ref.current
        if (!element) {
          return
        }
        if (element.paused || element.ended) {
          void element.play().catch(() => undefined)
        } else {
          element.pause()
        }
      },
      seek(seconds: number) {
        if (ref.current) {
          ref.current.currentTime = seconds
        }
      },
      /** 1× → 1,5× → 2× → 1×, como no tocador global. */
      cycleRate() {
        const element = ref.current
        if (element) {
          const index = RATES.indexOf(
            element.playbackRate as (typeof RATES)[number]
          )
          element.playbackRate = RATES[(index + 1) % RATES.length]
        }
      },
      toggleMuted() {
        if (ref.current) {
          ref.current.muted = !ref.current.muted
        }
      },
    }),
    [ref]
  )

  return [state, actions] as const
}
