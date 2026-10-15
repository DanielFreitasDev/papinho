/** Emoji do emojibase (forma compacta) que o seletor usa. */
export type EmojiItem = {
  unicode: string
  label: string
  tags: string[]
  group: number
  order: number
}

type CompactEmoji = {
  unicode: string
  label: string
  tags?: string[]
  group?: number
  order?: number
}

/** Grupos mostrados nas abas, na ordem do emojibase (o 2, "componentes", fica de fora). */
export const GROUPS = [0, 1, 3, 4, 5, 6, 7, 8, 9] as const

const loaders: Record<string, () => Promise<{ default: CompactEmoji[] }>> = {
  pt: () => import("emojibase-data/pt/compact.json"),
  en: () => import("emojibase-data/en/compact.json"),
}

const cache = new Map<string, Promise<EmojiItem[]>>()

/** Carrega os emojis do idioma sob demanda (pedaço próprio do bundle, servido pelo JAR). */
export function loadEmojis(language: string): Promise<EmojiItem[]> {
  const key = language.startsWith("pt") ? "pt" : "en"
  let pending = cache.get(key)
  if (!pending) {
    pending = loaders[key]().then((module) =>
      module.default
        .filter(
          (emoji): emoji is Required<CompactEmoji> =>
            emoji.group !== undefined && emoji.group !== 2
        )
        .map((emoji) => ({
          unicode: emoji.unicode,
          label: emoji.label,
          tags: emoji.tags ?? [],
          group: emoji.group,
          order: emoji.order ?? 0,
        }))
        .sort((a, b) => a.order - b.order)
    )
    cache.set(key, pending)
  }
  return pending
}

/** Busca sem acento nem caixa, no nome e nas etiquetas. */
export function normalize(text: string) {
  return text.normalize("NFD").replace(/\p{M}/gu, "").toLocaleLowerCase()
}

export function searchEmojis(emojis: EmojiItem[], query: string, limit = 120) {
  const term = normalize(query.trim())
  if (term === "") {
    return []
  }
  return emojis
    .filter(
      (emoji) =>
        normalize(emoji.label).includes(term) ||
        emoji.tags.some((tag) => normalize(tag).startsWith(term))
    )
    .slice(0, limit)
}

const RECENT_LIMIT = 24

function recentKey(userId: string) {
  return `papinho:emoji-recentes:${userId}`
}

/** Emojis usados por último neste navegador (conveniência local, por pessoa). */
export function readRecent(userId: string): string[] {
  try {
    const value = JSON.parse(localStorage.getItem(recentKey(userId)) ?? "[]")
    return Array.isArray(value)
      ? value.filter((item) => typeof item === "string")
      : []
  } catch {
    return []
  }
}

export function pushRecent(userId: string, emoji: string): string[] {
  const next = [
    emoji,
    ...readRecent(userId).filter((item) => item !== emoji),
  ].slice(0, RECENT_LIMIT)
  try {
    localStorage.setItem(recentKey(userId), JSON.stringify(next))
  } catch {
    // Armazenamento indisponível: os recentes ficam só nesta sessão.
  }
  return next
}
