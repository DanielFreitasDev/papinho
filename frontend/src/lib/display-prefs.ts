import { useSyncExternalStore } from "react"

/**
 * Preferências de exibição deste aparelho (UI-02): papel de parede da conversa, tamanho da fonte e densidade.
 * Aplicadas como atributos no <html> (data-wallpaper, data-font-size, data-density) e resolvidas no CSS, sem
 * estilo inline (CSP).
 */
export const WALLPAPERS = ["none", "dots", "grid", "waves", "tint"] as const
export const FONT_SIZES = ["small", "normal", "large", "xlarge"] as const
export const DENSITIES = ["comfortable", "compact"] as const

export type DisplayPrefs = {
  wallpaper: (typeof WALLPAPERS)[number]
  fontSize: (typeof FONT_SIZES)[number]
  density: (typeof DENSITIES)[number]
}

const KEY = "papinho.display"
const DEFAULTS: DisplayPrefs = {
  wallpaper: "none",
  fontSize: "normal",
  density: "comfortable",
}
const listeners = new Set<() => void>()
let current = read()

function read(): DisplayPrefs {
  try {
    const stored = JSON.parse(localStorage.getItem(KEY) ?? "{}") as Partial<
      Record<keyof DisplayPrefs, string>
    >
    return {
      wallpaper: pick(WALLPAPERS, stored.wallpaper, DEFAULTS.wallpaper),
      fontSize: pick(FONT_SIZES, stored.fontSize, DEFAULTS.fontSize),
      density: pick(DENSITIES, stored.density, DEFAULTS.density),
    }
  } catch {
    return DEFAULTS
  }
}

function pick<T extends string>(
  allowed: readonly T[],
  value: string | undefined,
  fallback: T
) {
  return allowed.includes(value as T) ? (value as T) : fallback
}

/** Aplica no <html>; chamado ao abrir o app (antes de desenhar) e a cada mudança. */
export function applyDisplayPrefs(prefs: DisplayPrefs = current) {
  const root = document.documentElement
  root.dataset.wallpaper = prefs.wallpaper
  root.dataset.fontSize = prefs.fontSize
  root.dataset.density = prefs.density
}

export function setDisplayPrefs(change: Partial<DisplayPrefs>) {
  current = { ...current, ...change }
  try {
    localStorage.setItem(KEY, JSON.stringify(current))
  } catch {
    // Sem localStorage vale só nesta aba.
  }
  applyDisplayPrefs(current)
  listeners.forEach((listener) => listener())
}

export function useDisplayPrefs() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => current
  )
}
