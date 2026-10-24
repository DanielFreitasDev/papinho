import { useMediaQuery } from "@/hooks/use-media-query"

const REDUCED_MOTION = "(prefers-reduced-motion: reduce)"

/** Durações de --duration-* no index.css, para o que espera uma animação em JS (o CSS é a fonte; isto é o padrão). */
const DURATIONS = {
  instant: 100,
  fast: 160,
  base: 240,
  slow: 360,
  long: 1000,
} as const

export type MotionDuration = keyof typeof DURATIONS

/** Curvas de --ease-* no index.css, para animações feitas em JS (a Web Animations API não aceita var()). */
const EASINGS = {
  standard: "cubic-bezier(0.2, 0, 0, 1)",
  enter: "cubic-bezier(0.05, 0.7, 0.1, 1)",
  exit: "cubic-bezier(0.3, 0, 0.8, 0.15)",
} as const

export type MotionEase = keyof typeof EASINGS

/**
 * Movimento reduzido pedido pelo sistema. As animações de CSS já param pela regra do index.css; isto vale para o que
 * roda em JS (sequências que esperam o fim, rolagem suave, onda da gravação).
 */
export function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia(REDUCED_MOTION).matches
  )
}

export function useReducedMotion(): boolean {
  return useMediaQuery(REDUCED_MOTION)
}

/** Rolagem suave, ou instantânea com movimento reduzido. */
export function scrollBehavior(): ScrollBehavior {
  return prefersReducedMotion() ? "auto" : "smooth"
}

/** Duração de um token em ms, lida do CSS quando há folha carregada (os testes sem CSS usam o padrão). */
export function motionMs(token: MotionDuration): number {
  if (typeof window !== "undefined") {
    const value = getComputedStyle(document.documentElement)
      .getPropertyValue(`--duration-${token}`)
      .trim()
    const parsed = value.endsWith("ms")
      ? Number.parseFloat(value)
      : value.endsWith("s")
        ? Number.parseFloat(value) * 1000
        : Number.NaN
    if (Number.isFinite(parsed)) {
      return parsed
    }
  }
  return DURATIONS[token]
}

/** Curva de um token, lida do CSS quando há folha carregada. */
export function motionEase(token: MotionEase): string {
  if (typeof window !== "undefined") {
    const value = getComputedStyle(document.documentElement)
      .getPropertyValue(`--ease-${token}`)
      .trim()
    if (value) {
      return value
    }
  }
  return EASINGS[token]
}
