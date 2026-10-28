import { isMac } from "@/lib/shortcuts"
import {
  scanMessage,
  type PositionedBlock,
  type PositionedInline,
  type Span,
} from "./parse"

/**
 * Formatação no campo de mensagem (pedido do dono de 2026-10-28), sobre a mesma análise das bolhas (parse.ts): como
 * desenhar o texto enquanto se digita, o que já está aplicado na seleção e a edição que cada botão da barra faz. Os
 * marcadores continuam os do WhatsApp (MS-01), e não os do Markdown: as mensagens já enviadas usam "*" para negrito,
 * que no Markdown seria itálico.
 */

export type InlineFormat = "bold" | "italic" | "strike" | "code" | "spoiler"
export type BlockFormat = "quote" | "bulletList" | "orderedList" | "codeBlock"
export type Format = InlineFormat | BlockFormat

export const INLINE_FORMATS: InlineFormat[] = [
  "bold",
  "italic",
  "strike",
  "code",
  "spoiler",
]
export const BLOCK_FORMATS: BlockFormat[] = [
  "quote",
  "bulletList",
  "orderedList",
  "codeBlock",
]

const TOKENS: Record<InlineFormat, string> = {
  bold: "*",
  italic: "_",
  strike: "~",
  code: "`",
  spoiler: "||",
}

/** Aparência de um trecho do campo; "marker" são os próprios marcadores (ficam visíveis, mais apagados). */
export type Look =
  | "bold"
  | "italic"
  | "strike"
  | "spoiler"
  | "code"
  | "codeBlock"
  | "marker"
  | "link"
  | "mention"

/** Trecho contínuo do texto com a mesma aparência; os trechos cobrem o texto inteiro, em ordem. */
export type Segment = Span & { looks: Look[] }

/** Troca de `from` até antes de `to` por `insert` e a seleção depois da troca (um passo só no desfazer). */
export type Edit = Span & { insert: string; selection: [number, number] }

type Mark = Span & { look: Look }

/** Um nó de formatação em linha (negrito, código...) com onde ficam os marcadores. */
type FormatNode = Span & { format: InlineFormat; marker: number }

const QUOTE_PREFIX = /^> ?/
const BULLET = /^[-*•] +/
const ORDERED = /^(\d{1,3})[.)] +/

export function segments(text: string): Segment[] {
  const marks: Mark[] = []
  collectMarks(scanMessage(text), marks)
  const events = marks
    .filter((mark) => mark.to > mark.from)
    .flatMap((mark) => [
      { at: mark.from, look: mark.look, delta: 1 },
      { at: mark.to, look: mark.look, delta: -1 },
    ])
    .sort((a, b) => a.at - b.at)
  const counts = new Map<Look, number>()
  const result: Segment[] = []
  let cursor = 0
  let next = 0
  while (cursor < text.length) {
    while (next < events.length && events[next].at <= cursor) {
      const event = events[next]
      counts.set(event.look, (counts.get(event.look) ?? 0) + event.delta)
      next++
    }
    const to = Math.min(
      next < events.length ? events[next].at : text.length,
      text.length
    )
    const looks = [...counts]
      .filter(([, count]) => count > 0)
      .map(([look]) => look)
    const previous = result.at(-1)
    if (previous && sameLooks(previous.looks, looks)) {
      previous.to = to
    } else {
      result.push({ from: cursor, to, looks })
    }
    cursor = to
  }
  return result
}

function sameLooks(a: Look[], b: Look[]) {
  return a.length === b.length && a.every((look) => b.includes(look))
}

function collectMarks(blocks: PositionedBlock[], marks: Mark[]) {
  for (const block of blocks) {
    switch (block.type) {
      case "paragraph":
        collectInline(block.children, marks)
        break
      case "code":
        block.markers.forEach((span) => marks.push({ ...span, look: "marker" }))
        block.body.forEach((span) => marks.push({ ...span, look: "codeBlock" }))
        break
      case "quote":
        block.markers.forEach((span) => marks.push({ ...span, look: "marker" }))
        collectMarks(block.children, marks)
        break
      case "list":
        block.items.forEach((item) => {
          marks.push({ ...item.marker, look: "marker" })
          collectInline(item.children, marks)
        })
        break
    }
  }
}

function collectInline(nodes: PositionedInline[], marks: Mark[]) {
  for (const node of nodes) {
    switch (node.type) {
      case "code":
        marks.push(
          { from: node.from, to: node.from + 1, look: "marker" },
          { from: node.from + 1, to: node.to - 1, look: "code" },
          { from: node.to - 1, to: node.to, look: "marker" }
        )
        break
      case "link":
      case "mention":
        marks.push({ from: node.from, to: node.to, look: node.type })
        break
      case "bold":
      case "italic":
      case "strike":
      case "spoiler":
        marks.push(
          { from: node.from, to: node.from + node.marker, look: "marker" },
          {
            from: node.from + node.marker,
            to: node.to - node.marker,
            look: node.type,
          },
          { from: node.to - node.marker, to: node.to, look: "marker" }
        )
        collectInline(node.children, marks)
        break
    }
  }
}

/** O texto já analisado: as duas leituras saem da mesma passada pelo parser. */
export type Analysis = {
  text: string
  blocks: PositionedBlock[]
  segments: Segment[]
}

export function analyze(text: string): Analysis {
  return { text, blocks: scanMessage(text), segments: segments(text) }
}

function looksAt(analysis: Analysis, index: number): Look[] {
  const list = analysis.segments
  let low = 0
  let high = list.length - 1
  while (low <= high) {
    const middle = (low + high) >> 1
    const segment = list[middle]
    if (index < segment.from) {
      high = middle - 1
    } else if (index >= segment.to) {
      low = middle + 1
    } else {
      return segment.looks
    }
  }
  return []
}

/**
 * O formato vale para a seleção quando todo caractere visível dela (fora espaços e marcadores) já o tem; com o
 * cursor sem seleção, vale o caractere antes dele (ou depois, se antes vem um marcador).
 */
function covered(analysis: Analysis, start: number, end: number, look: Look) {
  const { text } = analysis
  if (start === end) {
    for (const index of [start - 1, start]) {
      if (index < 0 || index >= text.length || text[index] === "\n") {
        continue
      }
      const looks = looksAt(analysis, index)
      if (!looks.includes("marker")) {
        return looks.includes(look)
      }
    }
    return false
  }
  let any = false
  for (const segment of analysis.segments) {
    if (segment.to <= start || segment.from >= end) {
      continue
    }
    if (segment.looks.includes("marker")) {
      continue
    }
    const piece = text.slice(
      Math.max(segment.from, start),
      Math.min(segment.to, end)
    )
    if (piece.trim() === "") {
      continue
    }
    any = true
    if (!segment.looks.includes(look)) {
      return false
    }
  }
  return any
}

/** As linhas que a seleção toca; a seleção que termina logo depois de um "\n" não pega a linha seguinte. */
function lineRange(text: string, start: number, end: number): Span {
  const from = start === 0 ? 0 : text.lastIndexOf("\n", start - 1) + 1
  const last = end > start && text[end - 1] === "\n" ? end - 1 : end
  const next = text.indexOf("\n", Math.max(last, from))
  return { from, to: next === -1 ? text.length : next }
}

function splitPrefix(line: string) {
  const quote = QUOTE_PREFIX.exec(line)?.[0] ?? ""
  const rest = line.slice(quote.length)
  const list = BULLET.exec(rest)?.[0] ?? ORDERED.exec(rest)?.[0] ?? ""
  return { quote, list, content: rest.slice(list.length) }
}

function linesOf(text: string, range: Span) {
  return text.slice(range.from, range.to).split("\n")
}

function codeBlockAt(analysis: Analysis, start: number, end: number) {
  const walk = (
    blocks: PositionedBlock[]
  ): Extract<PositionedBlock, { type: "code" }> | null => {
    for (const block of blocks) {
      if (block.type === "code") {
        const from = block.markers[0].from
        const to = block.markers.at(-1)!.to
        if (start >= from && end <= to) {
          return block
        }
      } else if (block.type === "quote") {
        const found = walk(block.children)
        if (found) {
          return found
        }
      }
    }
    return null
  }
  return walk(analysis.blocks)
}

/** Os formatos já aplicados no que está selecionado, para a barra mostrar os botões marcados. */
export function activeFormats(
  analysis: Analysis,
  start: number,
  end: number
): Set<Format> {
  const active = new Set<Format>()
  for (const format of INLINE_FORMATS) {
    if (covered(analysis, start, end, format)) {
      active.add(format)
    }
  }
  if (codeBlockAt(analysis, start, end)) {
    active.add("codeBlock")
    return active
  }
  const lines = linesOf(analysis.text, lineRange(analysis.text, start, end))
  const filled = lines.filter((line) => line.trim() !== "")
  if (filled.length > 0) {
    if (filled.every((line) => QUOTE_PREFIX.test(line))) {
      active.add("quote")
    }
    const parts = filled.map(splitPrefix)
    if (parts.every((part) => BULLET.test(part.list))) {
      active.add("bulletList")
    }
    if (parts.every((part) => ORDERED.test(part.list))) {
      active.add("orderedList")
    }
  }
  return active
}

/**
 * Dentro de código a formatação é literal: em código em linha, negrito, itálico, riscado e spoiler não valem; num
 * bloco de código, só tirar o próprio bloco.
 */
export function canFormat(
  analysis: Analysis,
  start: number,
  end: number,
  format: Format
) {
  if (format === "codeBlock") {
    return true
  }
  if (codeBlockAt(analysis, start, end)) {
    return false
  }
  if (format === "code") {
    return true
  }
  if (BLOCK_FORMATS.includes(format as BlockFormat)) {
    return true
  }
  return !covered(analysis, start, end, "code")
}

/** A edição de um botão (ou atalho) da barra: liga o formato na seleção, ou desliga se já estava aplicado. */
export function applyFormat(
  analysis: Analysis,
  start: number,
  end: number,
  format: Format
): Edit | null {
  if (!canFormat(analysis, start, end, format)) {
    return null
  }
  const active = activeFormats(analysis, start, end).has(format)
  if (start === end && format in TOKENS) {
    // O par vazio que o próprio botão pôs ("*|*") sai com o segundo toque.
    const token = TOKENS[format as InlineFormat]
    const around = analysis.text.slice(
      start - token.length,
      start + token.length
    )
    if (start >= token.length && around === token + token) {
      return {
        from: start - token.length,
        to: start + token.length,
        insert: "",
        selection: [start - token.length, start - token.length],
      }
    }
  }
  switch (format) {
    case "quote":
    case "bulletList":
    case "orderedList":
      return toggleLines(analysis.text, start, end, format, active)
    case "codeBlock":
      return active
        ? removeCodeBlock(analysis, start, end)
        : wrapCodeBlock(analysis.text, start, end)
    default:
      return active
        ? unwrapInline(analysis, start, end, format)
        : wrapInline(analysis, start, end, format)
  }
}

function formatNodes(blocks: PositionedBlock[]): FormatNode[] {
  const nodes: FormatNode[] = []
  const inline = (list: PositionedInline[]) => {
    for (const node of list) {
      if (node.type === "code") {
        nodes.push({ from: node.from, to: node.to, format: "code", marker: 1 })
      } else if (
        node.type === "bold" ||
        node.type === "italic" ||
        node.type === "strike" ||
        node.type === "spoiler"
      ) {
        nodes.push({
          from: node.from,
          to: node.to,
          format: node.type,
          marker: node.marker,
        })
        inline(node.children)
      }
    }
  }
  const walk = (list: PositionedBlock[]) => {
    for (const block of list) {
      if (block.type === "paragraph") {
        inline(block.children)
      } else if (block.type === "quote") {
        walk(block.children)
      } else if (block.type === "list") {
        block.items.forEach((item) => inline(item.children))
      }
    }
  }
  walk(blocks)
  return nodes
}

/** Tira os caracteres de `removed` de [from, to) e diz para onde cada posição antiga vai. */
function removeChars(text: string, span: Span, removed: Set<number>) {
  let insert = ""
  for (let index = span.from; index < span.to; index++) {
    if (!removed.has(index)) {
      insert += text[index]
    }
  }
  const map = (position: number) => {
    let shift = 0
    for (const index of removed) {
      if (index < position) {
        shift++
      }
    }
    return position - shift
  }
  return { insert, map }
}

function markerChars(nodes: FormatNode[]) {
  const removed = new Set<number>()
  for (const node of nodes) {
    for (let index = 0; index < node.marker; index++) {
      removed.add(node.from + index)
      removed.add(node.to - 1 - index)
    }
  }
  return removed
}

function unwrapInline(
  analysis: Analysis,
  start: number,
  end: number,
  format: InlineFormat
): Edit | null {
  const hit = formatNodes(analysis.blocks).filter(
    (node) =>
      node.format === format &&
      (start === end
        ? node.from < start && start < node.to
        : node.from < end && start < node.to)
  )
  if (hit.length === 0) {
    return null
  }
  const span = {
    from: Math.min(...hit.map((node) => node.from)),
    to: Math.max(...hit.map((node) => node.to)),
  }
  const { insert, map } = removeChars(analysis.text, span, markerChars(hit))
  return { ...span, insert, selection: [map(start), map(end)] }
}

const WORD = /[\p{L}\p{N}]/u

function isWord(char: string | undefined) {
  return char !== undefined && WORD.test(char)
}

/** O que a pessoa quis marcar: a palavra inteira (os marcadores só valem nas bordas das palavras), sem os espaços. */
function wordRange(text: string, start: number, end: number): Span | null {
  let from = start
  let to = end
  if (from === to) {
    while (from > 0 && isWord(text[from - 1])) {
      from--
    }
    while (to < text.length && isWord(text[to])) {
      to++
    }
    return to > from ? { from, to } : null
  }
  while (from > 0 && isWord(text[from - 1]) && isWord(text[from])) {
    from--
  }
  while (to < text.length && isWord(text[to - 1]) && isWord(text[to])) {
    to++
  }
  return { from, to }
}

function wrapInline(
  analysis: Analysis,
  start: number,
  end: number,
  format: InlineFormat
): Edit {
  const { text } = analysis
  const token = TOKENS[format]
  const range = wordRange(text, start, end)
  if (!range || text.slice(range.from, range.to).trim() === "") {
    // Sem palavra para marcar: o par de marcadores com o cursor no meio, pronto para digitar.
    return {
      from: end,
      to: end,
      insert: token + token,
      selection: [end + token.length, end + token.length],
    }
  }
  // O mesmo formato que encosta na seleção é absorvido: "a *b* c" vira "*a b c*", sem marcadores aninhados.
  let { from, to } = range
  const absorbed = formatNodes(analysis.blocks).filter(
    (node) => node.format === format && node.from < to && from < node.to
  )
  for (const node of absorbed) {
    from = Math.min(from, node.from)
    to = Math.max(to, node.to)
  }
  const removed = markerChars(absorbed)
  let insert = ""
  let first = -1
  let last = -1
  let lineStart = from
  while (lineStart <= to) {
    const newline = text.indexOf("\n", lineStart)
    const lineEnd = newline === -1 || newline > to ? to : newline
    // No começo de uma linha, o "> " e o "- " ficam de fora dos marcadores (senão a lista e a citação quebram).
    let contentStart = lineStart
    if (lineStart === 0 || text[lineStart - 1] === "\n") {
      const fullEnd = text.indexOf("\n", lineStart)
      const prefix = splitPrefix(
        text.slice(lineStart, fullEnd === -1 ? text.length : fullEnd)
      )
      contentStart = Math.min(
        lineStart + prefix.quote.length + prefix.list.length,
        lineEnd
      )
    }
    let coreStart = contentStart
    let coreEnd = lineEnd
    while (
      coreStart < coreEnd &&
      (/\s/.test(text[coreStart]) || removed.has(coreStart))
    ) {
      coreStart++
    }
    while (
      coreEnd > coreStart &&
      (/\s/.test(text[coreEnd - 1]) || removed.has(coreEnd - 1))
    ) {
      coreEnd--
    }
    const keep = (from: number, to: number) =>
      removeChars(text, { from, to }, removed).insert
    insert += keep(lineStart, coreStart)
    if (coreEnd > coreStart) {
      insert += token
      if (first === -1) {
        first = from + insert.length
      }
      insert += keep(coreStart, coreEnd)
      last = from + insert.length
      insert += token
    }
    insert += keep(coreEnd, lineEnd)
    if (lineEnd >= to) {
      break
    }
    insert += "\n"
    lineStart = lineEnd + 1
  }
  return {
    from,
    to,
    insert,
    selection: first === -1 ? [from, from] : [first, last],
  }
}

/** Citação e listas valem por linha: liga em todas as linhas tocadas ou desliga de todas. */
function toggleLines(
  text: string,
  start: number,
  end: number,
  format: "quote" | "bulletList" | "orderedList",
  active: boolean
): Edit {
  const range = lineRange(text, start, end)
  const lines = linesOf(text, range)
  // A lista numerada continua a contagem da linha de cima, se ela já for um item.
  const above = text
    .slice(0, Math.max(range.from - 1, 0))
    .split("\n")
    .at(-1)
  const aboveNumber =
    range.from > 0 && above !== undefined
      ? ORDERED.exec(splitPrefix(above).list)?.[1]
      : undefined
  let number = aboveNumber ? Number(aboveNumber) + 1 : 1
  const only = lines.length === 1
  const changed = lines.map((line) => {
    const { quote, list, content } = splitPrefix(line)
    let next: string
    let keepFrom: number
    if (format === "quote") {
      keepFrom = quote.length
      next = active
        ? line.slice(quote.length)
        : (quote === "" ? "> " : quote) + line.slice(quote.length)
      return {
        line,
        next,
        keepFrom,
        newKeepFrom: next.length - (line.length - keepFrom),
      }
    }
    keepFrom = quote.length + list.length
    if (active) {
      next = quote + content
    } else if (line.trim() === "" && !only) {
      next = line
      keepFrom = 0
    } else if (format === "bulletList") {
      next = `${quote}- ${content}`
    } else {
      next = `${quote}${number++}. ${content}`
    }
    return {
      line,
      next,
      keepFrom,
      newKeepFrom: next.length - (line.length - keepFrom),
    }
  })
  const insert = changed.map((item) => item.next).join("\n")
  const map = (position: number) => {
    let oldStart = range.from
    let newStart = range.from
    for (const item of changed) {
      const oldEnd = oldStart + item.line.length
      if (position <= oldEnd) {
        const offset = position - oldStart
        return offset >= item.keepFrom
          ? newStart + item.newKeepFrom + (offset - item.keepFrom)
          : newStart + item.newKeepFrom
      }
      oldStart = oldEnd + 1
      newStart += item.next.length + 1
    }
    return position - range.to + range.from + insert.length
  }
  return { ...range, insert, selection: [map(start), map(end)] }
}

/** Bloco de código pega linhas inteiras: as cercas ``` ficam sozinhas na linha de cima e na de baixo. */
function wrapCodeBlock(text: string, start: number, end: number): Edit {
  const range = lineRange(text, start, end)
  const body = text.slice(range.from, range.to)
  const insert = "```\n" + body + "\n```"
  const first = range.from + 4
  return { ...range, insert, selection: [first, first + body.length] }
}

function removeCodeBlock(
  analysis: Analysis,
  start: number,
  end: number
): Edit | null {
  const block = codeBlockAt(analysis, start, end)
  if (!block) {
    return null
  }
  const { text } = analysis
  const from = block.markers[0].from
  const lineEnd = text.indexOf("\n", block.markers.at(-1)!.to)
  const to = lineEnd === -1 ? text.length : lineEnd
  return {
    from,
    to,
    insert: block.value,
    selection: [from, from + block.value.length],
  }
}

/** A tecla de comando dos atalhos: ⌘ no Mac, Ctrl nos outros. */
export const MOD = isMac ? "⌘" : "Ctrl"

/** Atalhos da formatação no campo, como os do WhatsApp para computador; spoiler e bloco de código só pelos menus. */
export const FORMAT_KEYS: Partial<Record<Format, string[]>> = {
  bold: [MOD, "B"],
  italic: [MOD, "I"],
  strike: [MOD, "Shift", "X"],
  code: [MOD, "E"],
  orderedList: [MOD, "Shift", "7"],
  bulletList: [MOD, "Shift", "8"],
  quote: [MOD, "Shift", "9"],
}

/** Qual formato a tecla pede, ou null. A letra vale pela tecla digitada; os números, pela tecla física. */
export function formatShortcut(event: {
  ctrlKey: boolean
  metaKey: boolean
  altKey: boolean
  shiftKey: boolean
  key: string
  code: string
  isComposing?: boolean
}): Format | null {
  if (
    !(isMac ? event.metaKey : event.ctrlKey) ||
    event.altKey ||
    event.isComposing
  ) {
    return null
  }
  const key = event.key.toLowerCase()
  if (!event.shiftKey) {
    return key === "b"
      ? "bold"
      : key === "i"
        ? "italic"
        : key === "e"
          ? "code"
          : null
  }
  if (key === "x") {
    return "strike"
  }
  return event.code === "Digit7"
    ? "orderedList"
    : event.code === "Digit8"
      ? "bulletList"
      : event.code === "Digit9"
        ? "quote"
        : null
}
