import { useEffect, useRef } from "react"

/**
 * Atalhos do teclado como os do WhatsApp Web (MS-13). Um ouvinte só (no AppShell) reconhece a combinação e avisa por
 * um evento da janela; quem sabe fazer a ação (a conversa aberta, o campo, a lista) escuta com useShortcut. As teclas
 * são as físicas (event.code), então o mesmo atalho vale em qualquer layout de teclado; e o AltGr (Ctrl+Alt no
 * Windows, usado para digitar "/", "€", "°"...) nunca vira atalho.
 */
export type ShortcutName =
  | "markUnread"
  | "archive"
  | "pin"
  | "search"
  | "searchChat"
  | "newChat"
  | "nextChat"
  | "previousChat"
  | "newGroup"
  | "profile"
  | "settings"
  | "emoji"

type Combo = { alt: boolean; shift: boolean; code: string; key: string }

/** Ctrl (⌘ no Mac) mais estas teclas. */
export const SHORTCUTS: Record<ShortcutName, Combo> = {
  markUnread: { alt: true, shift: true, code: "KeyU", key: "U" },
  archive: { alt: true, shift: true, code: "KeyE", key: "E" },
  pin: { alt: true, shift: true, code: "KeyP", key: "P" },
  search: { alt: true, shift: false, code: "Slash", key: "/" },
  searchChat: { alt: false, shift: true, code: "KeyF", key: "F" },
  newChat: { alt: true, shift: false, code: "KeyN", key: "N" },
  nextChat: { alt: true, shift: true, code: "BracketRight", key: "]" },
  previousChat: { alt: true, shift: true, code: "BracketLeft", key: "[" },
  newGroup: { alt: true, shift: true, code: "KeyN", key: "N" },
  profile: { alt: true, shift: false, code: "KeyP", key: "P" },
  settings: { alt: true, shift: false, code: "Comma", key: "," },
  emoji: { alt: true, shift: false, code: "KeyE", key: "E" },
}

const EVENT = "papinho:shortcut"

export const isMac =
  typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform)

/** Qual atalho a tecla é, ou null. */
export function matchShortcut(
  event: Pick<
    KeyboardEvent,
    | "ctrlKey"
    | "metaKey"
    | "altKey"
    | "shiftKey"
    | "code"
    | "repeat"
    | "isComposing"
  > & { getModifierState?: (key: string) => boolean }
): ShortcutName | null {
  if (
    event.repeat ||
    event.isComposing ||
    event.getModifierState?.("AltGraph") ||
    !(event.ctrlKey || event.metaKey)
  ) {
    return null
  }
  for (const [name, combo] of Object.entries(SHORTCUTS) as [
    ShortcutName,
    Combo,
  ][]) {
    if (
      combo.code === event.code &&
      combo.alt === event.altKey &&
      combo.shift === event.shiftKey
    ) {
      return name
    }
  }
  return null
}

/** As teclas do atalho para mostrar na lista ("Ctrl", "Alt", "Shift", "U"). */
export function shortcutKeys(name: ShortcutName): string[] {
  const combo = SHORTCUTS[name]
  return [
    isMac ? "⌘" : "Ctrl",
    ...(combo.alt ? [isMac ? "⌥" : "Alt"] : []),
    ...(combo.shift ? ["Shift"] : []),
    combo.key,
  ]
}

/** Avisa quem escuta o atalho; devolve se alguém tratou (cancelou o evento). */
export function emitShortcut(name: ShortcutName) {
  return !window.dispatchEvent(
    new CustomEvent(EVENT, { detail: name, cancelable: true })
  )
}

/** Faz a ação do atalho enquanto o componente está na tela. O handler pode mudar a cada desenho. */
export function useShortcut(name: ShortcutName, handler: () => void) {
  const latest = useRef(handler)
  useEffect(() => {
    latest.current = handler
  })
  useEffect(() => {
    const listener = (event: Event) => {
      if ((event as CustomEvent<ShortcutName>).detail === name) {
        event.preventDefault()
        latest.current()
      }
    }
    window.addEventListener(EVENT, listener)
    return () => window.removeEventListener(EVENT, listener)
  }, [name])
}
