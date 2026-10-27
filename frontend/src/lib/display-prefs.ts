import { useSyncExternalStore } from "react"

/**
 * Preferências de exibição deste aparelho (UI-02): paleta de cor, papel de parede da conversa, tamanho da fonte e
 * densidade. Aplicadas como atributos no <html> (data-palette, data-wallpaper, data-font-size, data-density) e
 * resolvidas no CSS, sem estilo inline (CSP).
 */
/** Paletas do index.css; "grafite" é o preto e branco original. A primeira é o padrão. */
export const PALETTES = [
  "papinho",
  "oceano",
  "lavanda",
  "rosa",
  "coral",
  "ambar",
  "agua",
  "vinho",
  "grafite",
] as const
export const WALLPAPERS = ["none", "dots", "grid", "waves", "tint"] as const
export const FONT_SIZES = ["small", "normal", "large", "xlarge"] as const
export const DENSITIES = ["comfortable", "compact"] as const

export type DisplayPrefs = {
  palette: (typeof PALETTES)[number]
  wallpaper: (typeof WALLPAPERS)[number]
  fontSize: (typeof FONT_SIZES)[number]
  density: (typeof DENSITIES)[number]
}

const KEY = "papinho.display"
const DEFAULTS: DisplayPrefs = {
  palette: "papinho",
  wallpaper: "none",
  fontSize: "normal",
  density: "comfortable",
}
const listeners = new Set<() => void>()
let current = parseDisplayPrefs(readStored())

function readStored() {
  try {
    return localStorage.getItem(KEY)
  } catch {
    return null
  }
}

/** Lê o que está guardado; valor desconhecido (de outra versão, editado à mão) volta ao padrão. */
export function parseDisplayPrefs(raw: string | null): DisplayPrefs {
  try {
    const stored = JSON.parse(raw ?? "{}") as Partial<
      Record<keyof DisplayPrefs, string>
    >
    return {
      palette: pick(PALETTES, stored.palette, DEFAULTS.palette),
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
  root.dataset.palette = prefs.palette
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
