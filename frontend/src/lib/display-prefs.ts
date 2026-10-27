import { useSyncExternalStore } from "react"

/**
 * Preferências de exibição deste aparelho (UI-02): paleta de cor, papel de parede da conversa (com os desenhos e o
 * escurecimento da imagem própria), tamanho da fonte e densidade. Aplicadas como atributos no <html> (data-palette,
 * data-wallpaper, data-doodles, data-font-size, data-density) e resolvidas no CSS; só o escurecimento vai por uma
 * propriedade do CSSOM (a CSP barra estilo inline no HTML, não o CSSOM).
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
/**
 * Papéis de parede do index.css: "none" é a cor da conversa da paleta (o padrão), depois as cores, os degradês, os
 * padrões e a imagem escolhida pela pessoa (lib/wallpaper-image.ts).
 */
export const WALLPAPERS = [
  "none",
  "color-areia",
  "color-pessego",
  "color-rosa",
  "color-lilas",
  "color-ceu",
  "color-menta",
  "color-limao",
  "color-salvia",
  "color-ardosia",
  "color-oceano",
  "color-floresta",
  "color-noite",
  "gradient-aurora",
  "gradient-por-do-sol",
  "gradient-mar",
  "gradient-campo",
  "dots",
  "grid",
  "waves",
  "tint",
  "image",
] as const
/** Escurecimento da imagem própria, em %, para o texto sobre ela continuar legível. */
export const WALLPAPER_DIMS = [0, 10, 20, 30, 40, 50, 60] as const
export const FONT_SIZES = ["small", "normal", "large", "xlarge"] as const
export const DENSITIES = ["comfortable", "compact"] as const

export type DisplayPrefs = {
  palette: (typeof PALETTES)[number]
  wallpaper: (typeof WALLPAPERS)[number]
  /** Desenhos por cima da cor (como os do WhatsApp); não valem sobre a imagem própria. */
  doodles: boolean
  wallpaperDim: (typeof WALLPAPER_DIMS)[number]
  fontSize: (typeof FONT_SIZES)[number]
  density: (typeof DENSITIES)[number]
}

const KEY = "papinho.display"
const DEFAULTS: DisplayPrefs = {
  palette: "papinho",
  wallpaper: "none",
  doodles: true,
  wallpaperDim: 0,
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
      Record<keyof DisplayPrefs, unknown>
    >
    return {
      palette: pick(PALETTES, stored.palette, DEFAULTS.palette),
      wallpaper: pick(WALLPAPERS, stored.wallpaper, DEFAULTS.wallpaper),
      doodles:
        typeof stored.doodles === "boolean" ? stored.doodles : DEFAULTS.doodles,
      wallpaperDim: pick(
        WALLPAPER_DIMS,
        stored.wallpaperDim,
        DEFAULTS.wallpaperDim
      ),
      fontSize: pick(FONT_SIZES, stored.fontSize, DEFAULTS.fontSize),
      density: pick(DENSITIES, stored.density, DEFAULTS.density),
    }
  } catch {
    return DEFAULTS
  }
}

function pick<T extends string | number>(
  allowed: readonly T[],
  value: unknown,
  fallback: T
) {
  return allowed.includes(value as T) ? (value as T) : fallback
}

/** Aplica no <html>; chamado ao abrir o app (antes de desenhar) e a cada mudança. */
export function applyDisplayPrefs(prefs: DisplayPrefs = current) {
  const root = document.documentElement
  root.dataset.palette = prefs.palette
  root.dataset.wallpaper = prefs.wallpaper
  root.dataset.doodles = prefs.doodles ? "on" : "off"
  root.style.setProperty("--wallpaper-dim", String(prefs.wallpaperDim / 100))
  root.dataset.fontSize = prefs.fontSize
  root.dataset.density = prefs.density
}

/** Estado atual, fora do React (quem lê na hora de agir). */
export function getDisplayPrefs() {
  return current
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
