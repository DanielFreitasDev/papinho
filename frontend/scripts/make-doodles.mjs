// Gera public/wallpapers/doodles.svg: o padrão de desenhos do papel de parede da conversa (UI-02), como os "doodles"
// do WhatsApp, feito com ícones do Lucide (ISC), que o app já usa. O SVG serve de máscara no CSS (index.css): só a
// forma importa, a cor vem do tema. Determinístico (semente fixa): rodar de novo dá o mesmo arquivo.
//
//   node scripts/make-doodles.mjs
import { readFileSync, writeFileSync, mkdirSync } from "node:fs"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..")
const ICONS = [
  "heart", "star", "music", "coffee", "camera", "send", "smile", "sun", "moon", "cloud", "umbrella", "gift",
  "party-popper", "leaf", "flower", "mail", "pencil", "headphones", "zap", "message-circle", "clock", "key",
  "house", "bike", "cake", "pizza", "ice-cream-cone", "gamepad-2", "palette", "plane", "anchor", "rocket",
  "sparkles", "glasses", "cat", "dog", "fish", "bird", "mic", "phone", "bell", "map-pin", "mountain", "tree-pine",
  "cookie", "cherry", "apple", "book", "guitar", "balloon", "crown", "feather", "snowflake", "shell", "sailboat",
  "sprout", "clover", "film", "smartphone", "paw-print", "croissant", "lollipop", "dice-5", "puzzle", "lightbulb",
  "gem", "trophy", "thumbs-up", "laugh", "rainbow", "cloud-sun", "popcorn", "tent", "ticket", "watch",
]

/** Nós do ícone, lidos do pacote instalado (o mesmo desenho do app). */
function iconNodes(name) {
  const source = readFileSync(resolve(root, `node_modules/lucide-react/dist/esm/icons/${name}.mjs`), "utf8")
  // Nomes antigos só reexportam o ícone novo.
  const alias = /export \{ default \} from '\.\/([\w-]+)\.mjs'/.exec(source)
  if (alias) {
    return iconNodes(alias[1])
  }
  const nodes = []
  const element = /\[\s*"(\w+)",\s*\{([^}]*)\}\s*\]/g
  for (const match of source.matchAll(element)) {
    const attributes = [...match[2].matchAll(/(\w+):\s*"([^"]*)"/g)]
      .filter(([, key]) => key !== "key")
      .map(([, key, value]) => `${key}="${value}"`)
      .join(" ")
    nodes.push(`<${match[1]} ${attributes}/>`)
  }
  if (nodes.length === 0) {
    throw new Error(`ícone sem nós: ${name}`)
  }
  return nodes.join("")
}

/** Gerador pseudoaleatório com semente (mulberry32). */
function random(seed) {
  let state = seed
  return () => {
    state |= 0
    state = (state + 0x6d2b79f5) | 0
    let value = Math.imul(state ^ (state >>> 15), 1 | state)
    value = (value + Math.imul(value ^ (value >>> 7), 61 | value)) ^ value
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296
  }
}

const next = random(20261027)
const CELL = 56
const COLUMNS = 7
const SIZE = CELL * COLUMNS
const shuffled = [...ICONS].sort(() => next() - 0.5)
const parts = []
let index = 0
for (let row = 0; row < COLUMNS; row++) {
  for (let column = 0; column < COLUMNS; column++) {
    const name = shuffled[index++ % shuffled.length]
    const scale = 1 + next() * 0.3
    const size = 24 * scale
    // Dentro da célula, longe das bordas do ladrilho: nada corta na emenda da repetição.
    const free = CELL - size - 8
    const x = column * CELL + 4 + next() * free
    const y = row * CELL + 4 + next() * free
    const angle = Math.round((next() - 0.5) * 50)
    parts.push(
      `<g transform="translate(${x.toFixed(1)} ${y.toFixed(1)}) rotate(${angle} ${(size / 2).toFixed(1)} ${(size / 2).toFixed(1)}) scale(${scale.toFixed(2)})">${iconNodes(name)}</g>`
    )
  }
}

/** Aviso de copyright e permissão da licença ISC do Lucide, exigido em toda cópia. */
const LICENSE = readFileSync(resolve(root, "node_modules/lucide-react/LICENSE"), "utf8").trim().replace(/-{2,}/g, "-")
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${SIZE}" height="${SIZE}" viewBox="0 0 ${SIZE} ${SIZE}" fill="none" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
<!-- Gerado por frontend/scripts/make-doodles.mjs com ícones do Lucide (https://lucide.dev), licença ISC:
${LICENSE} -->
${parts.join("\n")}
</svg>
`
const target = resolve(root, "public/wallpapers/doodles.svg")
mkdirSync(dirname(target), { recursive: true })
writeFileSync(target, svg)
console.log(`${target}: ${svg.length} bytes, ${parts.length} desenhos`)
