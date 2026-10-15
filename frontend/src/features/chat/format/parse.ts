/**
 * Formatação do WhatsApp (MS-01) convertida numa árvore simples. Nada aqui gera HTML: o render monta elementos
 * React a partir da árvore, então o texto do usuário nunca é interpretado como marcação.
 *
 * Blocos: ``` (bloco de código, com linguagem opcional na primeira linha), citação "> ", listas "- ", "* " e "1. ".
 * Em linha: *negrito*, _itálico_, ~riscado~, `mono`, ||spoiler||, @usuario e links http(s).
 */

export type Inline =
  | { type: "text"; value: string }
  | { type: "break" }
  | { type: "code"; value: string }
  | { type: "mention"; username: string }
  | { type: "link"; href: string }
  | {
      type: "bold" | "italic" | "strike" | "spoiler"
      children: Inline[]
    }

export type Block =
  | { type: "paragraph"; children: Inline[] }
  | { type: "code"; language: string | null; value: string }
  | { type: "quote"; children: Block[] }
  | { type: "list"; ordered: boolean; start: number; items: Inline[][] }

type Marker = {
  token: string
  type: "bold" | "italic" | "strike" | "spoiler"
}

// "||" antes dos marcadores de um caractere: a ordem importa na busca.
const MARKERS: Marker[] = [
  { token: "||", type: "spoiler" },
  { token: "*", type: "bold" },
  { token: "_", type: "italic" },
  { token: "~", type: "strike" },
]

const BULLET = /^[-*•] +(.*)$/
const ORDERED = /^(\d{1,3})[.)] +(.*)$/
const QUOTE = /^> ?(.*)$/
const FENCE = /^```(.*)$/
const LANGUAGE = /^[A-Za-z][\w+#-]{0,19}$/
const LINK = /\bhttps?:\/\/[^\s<>"]+/gi
const MENTION = /(^|[^\w.@])@([a-z0-9._]{3,32})/gi

/** Texto da mensagem → blocos. */
export function parseMessage(text: string): Block[] {
  return parseBlocks(text.replace(/\r\n?/g, "\n").split("\n"))
}

function parseBlocks(lines: string[]): Block[] {
  const blocks: Block[] = []
  let paragraph: string[] = []
  const flush = () => {
    if (paragraph.length > 0) {
      blocks.push({ type: "paragraph", children: parseLines(paragraph) })
      paragraph = []
    }
  }

  for (let index = 0; index < lines.length; index++) {
    const line = lines[index]
    const fence = FENCE.exec(line)
    if (fence) {
      const rest = fence[1]
      // ```texto``` numa linha só: bloco mono de uma linha, como no WhatsApp.
      if (rest.length > 3 && rest.endsWith("```")) {
        flush()
        blocks.push({ type: "code", language: null, value: rest.slice(0, -3) })
        continue
      }
      const closing = lines.findIndex(
        (candidate, position) =>
          position > index && candidate.trimEnd().endsWith("```")
      )
      if (closing !== -1) {
        flush()
        const language = LANGUAGE.test(rest.trim()) ? rest.trim() : null
        const body = lines.slice(index + 1, closing)
        const last = lines[closing].trimEnd().slice(0, -3)
        if (last.length > 0) {
          body.push(last)
        }
        if (language === null && rest.trim().length > 0) {
          body.unshift(rest)
        }
        blocks.push({ type: "code", language, value: body.join("\n") })
        index = closing
        continue
      }
    }
    if (QUOTE.test(line)) {
      flush()
      const quoted: string[] = []
      while (index < lines.length && QUOTE.test(lines[index])) {
        quoted.push(QUOTE.exec(lines[index])![1])
        index++
      }
      index--
      blocks.push({ type: "quote", children: parseBlocks(quoted) })
      continue
    }
    const bullet = BULLET.exec(line)
    const ordered = ORDERED.exec(line)
    if (bullet || ordered) {
      flush()
      const isOrdered = !bullet
      const items: Inline[][] = []
      const start = ordered ? Number(ordered[1]) : 1
      while (index < lines.length) {
        const match = isOrdered
          ? ORDERED.exec(lines[index])
          : BULLET.exec(lines[index])
        if (!match) {
          break
        }
        items.push(parseInline(isOrdered ? match[2] : match[1]))
        index++
      }
      index--
      blocks.push({ type: "list", ordered: isOrdered, start, items })
      continue
    }
    paragraph.push(line)
  }
  flush()
  return blocks
}

function parseLines(lines: string[]): Inline[] {
  const result: Inline[] = []
  lines.forEach((line, index) => {
    if (index > 0) {
      result.push({ type: "break" })
    }
    result.push(...parseInline(line))
  })
  return result
}

/** Formatação em linha de um trecho sem quebras de linha. */
export function parseInline(text: string): Inline[] {
  const result: Inline[] = []
  // `mono` primeiro: o conteúdo é literal, sem formatação dentro.
  const code = /`([^`\n]+)`/g
  let cursor = 0
  for (const match of text.matchAll(code)) {
    result.push(...parseEmphasis(text.slice(cursor, match.index)))
    result.push({ type: "code", value: match[1] })
    cursor = match.index + match[0].length
  }
  result.push(...parseEmphasis(text.slice(cursor)))
  return result
}

function isBoundary(char: string | undefined): boolean {
  return char === undefined || /[\s\p{P}\p{S}]/u.test(char)
}

function parseEmphasis(text: string): Inline[] {
  if (text.length === 0) {
    return []
  }
  for (let start = 0; start < text.length; start++) {
    for (const marker of MARKERS) {
      if (!text.startsWith(marker.token, start)) {
        continue
      }
      const open = start + marker.token.length
      const before = text[start - 1]
      if (
        (start > 0 && !isBoundary(before)) ||
        before === marker.token[0] ||
        open >= text.length ||
        /\s/.test(text[open])
      ) {
        continue
      }
      let close = text.indexOf(marker.token, open)
      while (close !== -1) {
        const after = text[close + marker.token.length]
        if (
          close > open &&
          !/\s/.test(text[close - 1]) &&
          isBoundary(after) &&
          after !== marker.token[0]
        ) {
          return [
            ...parseLinks(text.slice(0, start)),
            {
              type: marker.type,
              children: parseEmphasis(text.slice(open, close)),
            },
            ...parseEmphasis(text.slice(close + marker.token.length)),
          ]
        }
        close = text.indexOf(marker.token, close + 1)
      }
    }
  }
  return parseLinks(text)
}

function parseLinks(text: string): Inline[] {
  const result: Inline[] = []
  let cursor = 0
  for (const match of text.matchAll(LINK)) {
    // Pontuação no fim ("veja https://x.com.") não faz parte do link.
    const href = match[0].replace(/[.,;:!?)\]}'"]+$/, "")
    result.push(...parseMentions(text.slice(cursor, match.index)))
    result.push({ type: "link", href })
    cursor = match.index + href.length
  }
  result.push(...parseMentions(text.slice(cursor)))
  return result
}

function parseMentions(text: string): Inline[] {
  const result: Inline[] = []
  let cursor = 0
  for (const match of text.matchAll(MENTION)) {
    const username = match[2].replace(/\.+$/, "")
    if (username.length < 3) {
      continue
    }
    const at = match.index + match[1].length
    if (at > cursor) {
      result.push({ type: "text", value: text.slice(cursor, at) })
    }
    result.push({ type: "mention", username: username.toLowerCase() })
    cursor = at + 1 + username.length
  }
  if (cursor < text.length) {
    result.push({ type: "text", value: text.slice(cursor) })
  }
  return result
}

/** Texto puro, sem marcadores, para prévias e leitores de tela. */
export function plainText(text: string): string {
  return flattenBlocks(parseMessage(text)).replace(/\s+/g, " ").trim()
}

function flattenBlocks(blocks: Block[]): string {
  return blocks
    .map((block) => {
      switch (block.type) {
        case "paragraph":
          return flattenInline(block.children)
        case "code":
          return block.value
        case "quote":
          return flattenBlocks(block.children)
        case "list":
          return block.items.map(flattenInline).join(" ")
      }
    })
    .join(" ")
}

function flattenInline(nodes: Inline[]): string {
  return nodes
    .map((node) => {
      switch (node.type) {
        case "text":
        case "code":
          return node.value
        case "break":
          return " "
        case "mention":
          return `@${node.username}`
        case "link":
          return node.href
        case "spoiler":
          return "▒▒▒"
        default:
          return flattenInline(node.children)
      }
    })
    .join("")
}
