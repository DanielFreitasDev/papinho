import { stemPortuguese } from "./stem-pt"

/**
 * Onde destacar o termo buscado (GR-06), pela mesma regra da busca do servidor (`MessageSearch`): sem diferenciar
 * acentos e maiúsculas, cada palavra da busca acha as palavras com o mesmo radical em português (com os plurais em
 * -ões, -ães, -ãos e -ns trocados pelo singular e sem as palavras vazias), e o trecho inteiro digitado acha o mesmo
 * trecho em qualquer palavra ("Fortal" acha "Fortaleza"). Só calcula posições; quem desenha monta elementos React.
 */

/** Palavras vazias do PostgreSQL (`portuguese.stop`) sem acento: a busca tira os acentos antes, então só elas casam. */
const STOP_WORDS = new Set(
  (
    "de a o que e do da em um para com uma os no se na por mais as dos como mas ao ele das seu sua ou quando muito " +
    "nos eu pelo pela isso ela entre depois sem mesmo aos seus quem nas me esse eles essa num nem suas meu minha " +
    "numa pelos elas qual lhe deles essas esses pelas este dele tu te vos lhes meus minhas teu tua teus tuas nosso " +
    "nossa nossos nossas dela delas esta estes estas aquele aquela aqueles aquelas isto aquilo estou estamos estive " +
    "esteve estivemos estiveram estava estavam estivera esteja estejamos estejam estivesse estivessem estiver " +
    "estivermos estiverem hei havemos houve houvemos houveram houvera haja hajamos hajam houvesse houvessem houver " +
    "houvermos houverem houverei houveremos houveria houveriam sou somos era eram fui foi fomos foram fora seja " +
    "sejamos sejam fosse fossem for formos forem serei seremos seria seriam tenho tem temos tinha tinham tive teve " +
    "tivemos tiveram tivera tenha tenhamos tenham tivesse tivessem tiver tivermos tiverem terei teremos teria teriam"
  ).split(" ")
)

/** Letras que o `unaccent` do PostgreSQL troca e a decomposição Unicode (NFD) não separa. */
const LETTERS: Record<string, string> = {
  æ: "ae",
  œ: "oe",
  ß: "ss",
  ø: "o",
  đ: "d",
  ð: "d",
  ł: "l",
  ŀ: "l",
  þ: "th",
  ı: "i",
  ĳ: "ij",
  ª: "a",
  º: "o",
  ﬀ: "ff",
  ﬁ: "fi",
  ﬂ: "fl",
  ﬃ: "ffi",
  ﬄ: "ffl",
}

/** Palavra como o analisador do PostgreSQL a separa: letras e dígitos decimais ("x²" é "x"). */
const WORD = /[\p{L}\p{Nd}]+/gu

/** Busca já preparada: o trecho inteiro e os radicais das palavras. */
export type SearchTerms = {
  phrase: string | null
  lexemes: ReadonlySet<string>
}

/** Texto sem acentos e em minúsculas, com a posição no original de cada caractere do resultado (e do fim). */
export function fold(text: string): { folded: string; origin: number[] } {
  let folded = ""
  const origin: number[] = []
  let index = 0
  for (const char of text) {
    const lower = char.toLowerCase()
    const plain = (LETTERS[lower] ?? lower.normalize("NFD")).replace(
      /\p{M}/gu,
      ""
    )
    // Uma posição por unidade do resultado: um emoji ocupa duas (par substituto).
    for (let unit = 0; unit < plain.length; unit++) {
      origin.push(index)
    }
    folded += plain
    index += char.length
  }
  origin.push(index)
  return { folded, origin }
}

/**
 * Radical da palavra já sem acentos, como o servidor indexa: plural pelo singular (`papinho_search_text`), palavra
 * vazia fora (nulo) e o radical do Snowball.
 */
export function lexeme(word: string): string | null {
  const singular = word.replace(/(oe|ae|ao)s$/, "ao").replace(/ns$/, "m")
  return STOP_WORDS.has(singular) ? null : stemPortuguese(singular)
}

/** Prepara a busca; nulo quando não há o que destacar. */
export function searchTerms(query: string): SearchTerms | null {
  const trimmed = query.trim()
  if (!trimmed) {
    return null
  }
  const lexemes = new Set<string>()
  // Sintaxe do websearch_to_tsquery: "or" liga palavras, "-palavra" exclui, aspas formam frase.
  for (const token of trimmed.split(/\s+/)) {
    if (token.startsWith("-") || token.toLowerCase() === "or") {
      continue
    }
    for (const word of fold(token).folded.match(WORD) ?? []) {
      const stem = lexeme(word)
      if (stem) {
        lexemes.add(stem)
      }
    }
  }
  // Uma letra só acharia quase todo texto: o trecho entra a partir de dois caracteres.
  const phrase = fold(trimmed).folded
  return { phrase: phrase.length >= 2 ? phrase : null, lexemes }
}

/** Trechos de `text` a destacar, em ordem e sem sobreposição: [início, fim) no texto original. */
export function findMatches(
  text: string,
  terms: SearchTerms
): [number, number][] {
  const { folded, origin } = fold(text)
  const ranges: [number, number][] = []
  const phrase = terms.phrase
  if (phrase) {
    for (
      let at = folded.indexOf(phrase);
      at !== -1;
      at = folded.indexOf(phrase, at + phrase.length)
    ) {
      ranges.push([origin[at], origin[at + phrase.length]])
    }
  }
  if (terms.lexemes.size > 0) {
    for (const match of folded.matchAll(WORD)) {
      const stem = lexeme(match[0])
      if (stem !== null && terms.lexemes.has(stem)) {
        ranges.push([
          origin[match.index],
          origin[match.index + match[0].length],
        ])
      }
    }
  }
  return merge(ranges)
}

/**
 * Recorte de uma linha em volta do primeiro trecho achado, para a lista de resultados mostrar o termo mesmo no fim
 * de uma mensagem longa; começa no início de uma palavra e ganha "…" quando corta o começo.
 */
export function excerpt(text: string, terms: SearchTerms, before = 24): string {
  const first = findMatches(text, terms)[0]
  if (!first || first[0] <= before) {
    return text
  }
  const space = text.lastIndexOf(" ", first[0] - before)
  let start = space === -1 ? first[0] - before : space + 1
  // Sem cortar um emoji (par substituto) ao meio.
  if (/[\uDC00-\uDFFF]/.test(text[start])) {
    start++
  }
  return `…${text.slice(start)}`
}

function merge(ranges: [number, number][]): [number, number][] {
  const sorted = ranges
    .filter(([start, end]) => end > start)
    .sort((a, b) => a[0] - b[0] || a[1] - b[1])
  const merged: [number, number][] = []
  for (const [start, end] of sorted) {
    const last = merged.at(-1)
    if (last && start <= last[1]) {
      last[1] = Math.max(last[1], end)
    } else {
      merged.push([start, end])
    }
  }
  return merged
}
