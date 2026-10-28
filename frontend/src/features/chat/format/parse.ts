/**
 * Formatação do WhatsApp (MS-01) convertida numa árvore simples. Nada aqui gera HTML: o render monta elementos
 * React a partir da árvore, então o texto do usuário nunca é interpretado como marcação.
 *
 * Blocos: ``` (bloco de código, com linguagem opcional na primeira linha), citação "> ", listas "- ", "* " e "1. ".
 * Em linha: *negrito*, _itálico_, ~riscado~, `mono`, ||spoiler||, @usuario e links http(s).
 *
 * A análise guarda onde cada trecho está no texto (scanMessage): o campo de mensagem usa as posições para mostrar a
 * formatação enquanto se digita e para a barra de formatação saber o que já está aplicado. A árvore das bolhas é a
 * mesma análise sem as posições, então as duas nunca discordam.
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

/** Trecho do texto, do caractere `from` até antes do `to`. */
export type Span = { from: number; to: number }

/** Inline com a posição no texto; nos marcadores, `marker` é o tamanho de cada um ("*" = 1, "||" = 2). */
export type PositionedInline = Span &
  (
    | { type: "text"; value: string }
    | { type: "break" }
    | { type: "code"; value: string }
    | { type: "mention"; username: string }
    | { type: "link"; href: string }
    | {
        type: "bold" | "italic" | "strike" | "spoiler"
        marker: number
        children: PositionedInline[]
      }
  )

/** Bloco com as posições: `markers` são as cercas ```, os "> " e os "- "/"1. "; `body`, as linhas do código. */
export type PositionedBlock =
  | { type: "paragraph"; children: PositionedInline[] }
  | {
      type: "code"
      language: string | null
      value: string
      markers: Span[]
      body: Span[]
    }
  | { type: "quote"; markers: Span[]; children: PositionedBlock[] }
  | {
      type: "list"
      ordered: boolean
      start: number
      items: { marker: Span; children: PositionedInline[] }[]
    }

/** Uma linha do texto e onde ela começa. */
type Line = { text: string; from: number }

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
  return scanMessage(text).map(toBlock)
}

/**
 * Como parseMessage, com a posição de cada trecho. As posições contam "\r\n" como um caractere só (o campo de
 * mensagem, que usa isto, só tem "\n").
 */
export function scanMessage(text: string): PositionedBlock[] {
  const lines: Line[] = []
  let from = 0
  for (const line of text.replace(/\r\n?/g, "\n").split("\n")) {
    lines.push({ text: line, from })
    from += line.length + 1
  }
  return scanBlocks(lines)
}

function scanBlocks(lines: Line[]): PositionedBlock[] {
  const blocks: PositionedBlock[] = []
  let paragraph: Line[] = []
  const flush = () => {
    if (paragraph.length > 0) {
      blocks.push({ type: "paragraph", children: scanLines(paragraph) })
      paragraph = []
    }
  }

  for (let index = 0; index < lines.length; index++) {
    const line = lines[index]
    const fence = FENCE.exec(line.text)
    if (fence) {
      const rest = fence[1]
      // ```texto``` numa linha só: bloco mono de uma linha, como no WhatsApp.
      if (rest.length > 3 && rest.endsWith("```")) {
        flush()
        const end = line.from + line.text.length
        blocks.push({
          type: "code",
          language: null,
          value: rest.slice(0, -3),
          markers: [
            { from: line.from, to: line.from + 3 },
            { from: end - 3, to: end },
          ],
          body: [{ from: line.from + 3, to: end - 3 }],
        })
        continue
      }
      const closing = lines.findIndex(
        (candidate, position) =>
          position > index && candidate.text.trimEnd().endsWith("```")
      )
      if (closing !== -1) {
        flush()
        const language = LANGUAGE.test(rest.trim()) ? rest.trim() : null
        const body = lines.slice(index + 1, closing)
        const fenceLine = lines[closing]
        const closeAt = fenceLine.from + fenceLine.text.trimEnd().length
        const last = fenceLine.text.trimEnd().slice(0, -3)
        if (last.length > 0) {
          body.push({ text: last, from: fenceLine.from })
        }
        // Sem linguagem, o que vem depois da cerca de abertura já é código.
        const inlineBody = language === null && rest.trim().length > 0
        const opening = inlineBody ? 3 : line.text.length
        if (inlineBody) {
          body.unshift({ text: rest, from: line.from + 3 })
        }
        blocks.push({
          type: "code",
          language,
          value: body.map((part) => part.text).join("\n"),
          markers: [
            { from: line.from, to: line.from + opening },
            { from: closeAt - 3, to: closeAt },
          ],
          body: body.map((part) => ({
            from: part.from,
            to: part.from + part.text.length,
          })),
        })
        index = closing
        continue
      }
    }
    if (QUOTE.test(line.text)) {
      flush()
      const quoted: Line[] = []
      const markers: Span[] = []
      while (index < lines.length && QUOTE.test(lines[index].text)) {
        const current = lines[index]
        const content = QUOTE.exec(current.text)![1]
        const prefix = current.text.length - content.length
        markers.push({ from: current.from, to: current.from + prefix })
        quoted.push({ text: content, from: current.from + prefix })
        index++
      }
      index--
      blocks.push({ type: "quote", markers, children: scanBlocks(quoted) })
      continue
    }
    const bullet = BULLET.exec(line.text)
    const ordered = ORDERED.exec(line.text)
    if (bullet || ordered) {
      flush()
      const isOrdered = !bullet
      const items: { marker: Span; children: PositionedInline[] }[] = []
      const start = ordered ? Number(ordered[1]) : 1
      while (index < lines.length) {
        const current = lines[index]
        const match = isOrdered
          ? ORDERED.exec(current.text)
          : BULLET.exec(current.text)
        if (!match) {
          break
        }
        const content = isOrdered ? match[2] : match[1]
        const prefix = current.text.length - content.length
        items.push({
          marker: { from: current.from, to: current.from + prefix },
          children: scanInline(content, current.from + prefix),
        })
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

function scanLines(lines: Line[]): PositionedInline[] {
  const result: PositionedInline[] = []
  lines.forEach((line, index) => {
    if (index > 0) {
      // A quebra é o "\n" logo depois da linha anterior (numa citação, antes do "> " desta).
      const previous = lines[index - 1]
      const at = previous.from + previous.text.length
      result.push({ type: "break", from: at, to: at + 1 })
    }
    result.push(...scanInline(line.text, line.from))
  })
  return result
}

/** Formatação em linha de um trecho sem quebras de linha. */
export function parseInline(text: string): Inline[] {
  return scanInline(text, 0).map(toInline)
}

/** Como parseInline, com as posições contadas a partir de `base`. */
function scanInline(text: string, base: number): PositionedInline[] {
  const result: PositionedInline[] = []
  // `mono` primeiro: o conteúdo é literal, sem formatação dentro.
  const code = /`([^`\n]+)`/g
  let cursor = 0
  for (const match of text.matchAll(code)) {
    result.push(...scanEmphasis(text.slice(cursor, match.index), base + cursor))
    result.push({
      type: "code",
      value: match[1],
      from: base + match.index,
      to: base + match.index + match[0].length,
    })
    cursor = match.index + match[0].length
  }
  result.push(...scanEmphasis(text.slice(cursor), base + cursor))
  return result
}

function isBoundary(char: string | undefined): boolean {
  return char === undefined || /[\s\p{P}\p{S}]/u.test(char)
}

function scanEmphasis(text: string, base: number): PositionedInline[] {
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
          const end = close + marker.token.length
          return [
            ...scanLinks(text.slice(0, start), base),
            {
              type: marker.type,
              marker: marker.token.length,
              from: base + start,
              to: base + end,
              children: scanEmphasis(text.slice(open, close), base + open),
            },
            ...scanEmphasis(text.slice(end), base + end),
          ]
        }
        close = text.indexOf(marker.token, close + 1)
      }
    }
  }
  return scanLinks(text, base)
}

function scanLinks(text: string, base: number): PositionedInline[] {
  const result: PositionedInline[] = []
  let cursor = 0
  for (const match of text.matchAll(LINK)) {
    // Pontuação no fim ("veja https://x.com.") não faz parte do link.
    const href = match[0].replace(/[.,;:!?)\]}'"]+$/, "")
    result.push(...scanMentions(text.slice(cursor, match.index), base + cursor))
    result.push({
      type: "link",
      href,
      from: base + match.index,
      to: base + match.index + href.length,
    })
    cursor = match.index + href.length
  }
  result.push(...scanMentions(text.slice(cursor), base + cursor))
  return result
}

function scanMentions(text: string, base: number): PositionedInline[] {
  const result: PositionedInline[] = []
  let cursor = 0
  for (const match of text.matchAll(MENTION)) {
    const username = match[2].replace(/\.+$/, "")
    if (username.length < 3) {
      continue
    }
    const at = match.index + match[1].length
    if (at > cursor) {
      result.push({
        type: "text",
        value: text.slice(cursor, at),
        from: base + cursor,
        to: base + at,
      })
    }
    result.push({
      type: "mention",
      username: username.toLowerCase(),
      from: base + at,
      to: base + at + 1 + username.length,
    })
    cursor = at + 1 + username.length
  }
  if (cursor < text.length) {
    result.push({
      type: "text",
      value: text.slice(cursor),
      from: base + cursor,
      to: base + text.length,
    })
  }
  return result
}

function toInline(node: PositionedInline): Inline {
  switch (node.type) {
    case "text":
      return { type: "text", value: node.value }
    case "break":
      return { type: "break" }
    case "code":
      return { type: "code", value: node.value }
    case "mention":
      return { type: "mention", username: node.username }
    case "link":
      return { type: "link", href: node.href }
    default:
      return { type: node.type, children: node.children.map(toInline) }
  }
}

function toBlock(block: PositionedBlock): Block {
  switch (block.type) {
    case "paragraph":
      return { type: "paragraph", children: block.children.map(toInline) }
    case "code":
      return { type: "code", language: block.language, value: block.value }
    case "quote":
      return { type: "quote", children: block.children.map(toBlock) }
    case "list":
      return {
        type: "list",
        ordered: block.ordered,
        start: block.start,
        items: block.items.map((item) => item.children.map(toInline)),
      }
  }
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

/** Primeiro link http(s) do texto (prévia, MD-04), fora de blocos de código. */
export function firstLink(text: string): string | null {
  const walk = (inline: Inline[]): string | null => {
    for (const node of inline) {
      if (node.type === "link") {
        return node.href
      }
      if ("children" in node) {
        const found = walk(node.children)
        if (found) {
          return found
        }
      }
    }
    return null
  }
  const blocks = (list: Block[]): string | null => {
    for (const block of list) {
      const found =
        block.type === "paragraph"
          ? walk(block.children)
          : block.type === "quote"
            ? blocks(block.children)
            : block.type === "list"
              ? (block.items.map(walk).find(Boolean) ?? null)
              : null
      if (found) {
        return found
      }
    }
    return null
  }
  return blocks(parseMessage(text))
}
