import { useSyncExternalStore } from "react"

/**
 * Preferências de digitação e envio deste aparelho, como as de "Conversas" do WhatsApp: Enter envia (no automático,
 * só com teclado físico: no toque o Enter quebra linha), verificação ortográfica, trocar ":)" por emoji ao digitar e o
 * som ao enviar. Ficam no aparelho, como as de exibição (lib/display-prefs.ts).
 */
export type ChatPrefs = {
  enterToSend: "auto" | "on" | "off"
  spellcheck: boolean
  emojiReplace: boolean
  sendSound: boolean
}

const KEY = "papinho.chat"
const DEFAULTS: ChatPrefs = {
  enterToSend: "auto",
  spellcheck: true,
  emojiReplace: true,
  sendSound: false,
}
const listeners = new Set<() => void>()
let current = parseChatPrefs(readStored())

function readStored() {
  try {
    return localStorage.getItem(KEY)
  } catch {
    return null
  }
}

/** Lê o que está guardado; valor desconhecido volta ao padrão. */
export function parseChatPrefs(raw: string | null): ChatPrefs {
  try {
    const stored = JSON.parse(raw ?? "{}") as Partial<
      Record<keyof ChatPrefs, unknown>
    >
    const flag = (key: "spellcheck" | "emojiReplace" | "sendSound") =>
      typeof stored[key] === "boolean" ? stored[key] : DEFAULTS[key]
    return {
      enterToSend: ["auto", "on", "off"].includes(String(stored.enterToSend))
        ? (stored.enterToSend as ChatPrefs["enterToSend"])
        : DEFAULTS.enterToSend,
      spellcheck: flag("spellcheck"),
      emojiReplace: flag("emojiReplace"),
      sendSound: flag("sendSound"),
    }
  } catch {
    return DEFAULTS
  }
}

export function getChatPrefs() {
  return current
}

export function setChatPrefs(change: Partial<ChatPrefs>) {
  current = { ...current, ...change }
  try {
    localStorage.setItem(KEY, JSON.stringify(current))
  } catch {
    // Sem localStorage vale só nesta aba.
  }
  listeners.forEach((listener) => listener())
}

export function useChatPrefs() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => current
  )
}

/** Enter envia agora? No automático, só com ponteiro fino (mouse): no celular o Enter quebra linha. */
export function enterSends(prefs: ChatPrefs, coarsePointer: boolean) {
  return prefs.enterToSend === "auto"
    ? !coarsePointer
    : prefs.enterToSend === "on"
}

/** Carinhas de texto que viram emoji ao digitar o espaço depois delas. */
const EMOTICONS: Record<string, string> = {
  ":)": "🙂",
  ":-)": "🙂",
  ":D": "😃",
  ":-D": "😃",
  ";)": "😉",
  ";-)": "😉",
  ":(": "🙁",
  ":-(": "🙁",
  ":P": "😛",
  ":p": "😛",
  ":-P": "😛",
  ":O": "😮",
  ":o": "😮",
  ":'(": "😢",
  "<3": "❤️",
  ":*": "😘",
  "(y)": "👍",
  "B)": "😎",
  ":|": "😐",
  xD: "😆",
  XD: "😆",
}

/**
 * Troca a carinha logo antes do espaço que acabou de ser digitado no cursor (":) " → "🙂 "). Só vale para a carinha
 * sozinha, no começo ou depois de um espaço: "http://", "a:)" e o texto já escrito ficam como estão. Devolve o texto e
 * o cursor novos, ou null se não há o que trocar.
 */
export function replaceEmoticon(
  text: string,
  caret: number
): { text: string; caret: number } | null {
  const before = text.slice(0, caret)
  const match = /(^|\s)(\S+)(\s)$/.exec(before)
  if (!match) {
    return null
  }
  const emoji = EMOTICONS[match[2]]
  if (!emoji) {
    return null
  }
  const start = before.length - match[2].length - 1
  const replaced = text.slice(0, start) + emoji + match[3] + text.slice(caret)
  return { text: replaced, caret: start + emoji.length + 1 }
}
