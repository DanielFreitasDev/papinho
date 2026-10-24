/**
 * Radical de uma palavra em português pelo algoritmo Snowball, o mesmo do dicionário `portuguese_stem` do PostgreSQL
 * que a busca do servidor usa (GR-06): o destaque marca as palavras que a busca achou ("falando" acha "falar"). Recebe a
 * palavra em minúsculas; a busca tira os acentos antes, então as regras com acento só valem para quem chamar sem
 * tirar. `stem-pt.fixture.tsv` traz os radicais que o próprio PostgreSQL devolveu.
 */

const VOWELS = new Set("aeiouáéíóúâêô")

/** Sufixos que saem quando estão em R2. */
const DELETE_IN_R2 = new Set(
  (
    "eza ezas ico ica icos icas ismo ismos ável ível ista istas oso osa osos osas amento amentos imento imentos " +
    "adora ador aça~o adoras adores aço~es ante antes ância"
  ).split(" ")
)
const STANDARD = [
  ...DELETE_IN_R2,
  ..."logia logias uça~o uço~es ência ências amente mente idade idades iva ivo ivas ivos ira iras".split(
    " "
  ),
]
const VERB = (
  "ada ida ia aria eria iria ará ara erá era irá ava asse esse isse aste este iste ei arei erei irei am iam ariam " +
  "eriam iriam aram eram iram avam em arem erem irem assem essem issem ado ido ando endo indo ara~o era~o ira~o ar " +
  "er ir as adas idas ias arias erias irias arás aras erás eras irás avas es ardes erdes irdes ares eres ires asses " +
  "esses isses astes estes istes is ais eis íeis aríeis eríeis iríeis áreis areis éreis ereis íreis ireis ásseis " +
  "ésseis ísseis áveis ados idos ámos amos íamos aríamos eríamos iríamos áramos éramos íramos ávamos emos aremos " +
  "eremos iremos ássemos êssemos íssemos imos armos ermos irmos eu iu ou ira iras"
).split(" ")
const RESIDUAL = "os a i o á í ó".split(" ")
const FORM = "e é ê ç".split(" ")

function isVowel(char: string | undefined) {
  return char !== undefined && VOWELS.has(char)
}

/** Posição logo depois da primeira vogal (ou consoante) a partir de `from`; o tamanho da palavra se não houver. */
function past(word: string, from: number, vowel: boolean) {
  for (let i = from; i < word.length; i++) {
    if (isVowel(word[i]) === vowel) {
      return i + 1
    }
  }
  return word.length
}

/** Regiões RV, R1 e R2 do Snowball. */
function regions(word: string) {
  let rv = word.length
  if (word.length >= 2) {
    if (isVowel(word[0])) {
      rv = past(word, 2, !isVowel(word[1]))
    } else {
      rv = isVowel(word[1]) ? Math.min(3, word.length) : past(word, 2, true)
    }
  }
  const r1 = past(word, past(word, 0, true), false)
  const r2 = past(word, past(word, r1, true), false)
  return { rv, r1, r2 }
}

/** O sufixo mais longo da lista que termina a palavra a partir de `limit`, ou nulo. */
function longest(word: string, suffixes: readonly string[], limit = 0) {
  let found: string | null = null
  for (const suffix of suffixes) {
    if (
      suffix.length > (found?.length ?? 0) &&
      word.length - suffix.length >= limit &&
      word.endsWith(suffix)
    ) {
      found = suffix
    }
  }
  return found
}

export function stemPortuguese(input: string): string {
  // "ã" e "õ" viram vogal + "~" (uma consoante para o algoritmo) e voltam no fim.
  let word = input.replace(/ã/g, "a~").replace(/õ/g, "o~")
  const { rv, r1, r2 } = regions(word)
  const startOf = (suffix: string) => word.length - suffix.length
  const cut = (suffix: string) => word.slice(0, startOf(suffix))
  // Sufixo seguinte (depois de tirar o principal) que também sai quando está em R2.
  const alsoInR2 = (suffixes: readonly string[]) => {
    const next = longest(word, suffixes)
    if (next !== null && startOf(next) >= r2) {
      word = cut(next)
      return next
    }
    return null
  }

  const standard = (): boolean => {
    const suffix = longest(word, STANDARD)
    if (suffix === null) {
      return false
    }
    const start = startOf(suffix)
    if (suffix === "amente") {
      if (start < r1) return false
      word = cut(suffix)
      if (alsoInR2(["iv", "os", "ic", "ad"]) === "iv") {
        alsoInR2(["at"])
      }
      return true
    }
    if (suffix === "ira" || suffix === "iras") {
      // Vira "ir" em RV, depois de "e" ("fronteira" → "fronteir").
      if (start < rv || word[start - 1] !== "e") return false
      word = cut(suffix) + "ir"
      return true
    }
    if (start < r2) {
      return false
    }
    word = cut(suffix)
    if (suffix === "logia" || suffix === "logias") {
      word += "log"
    } else if (suffix === "uça~o" || suffix === "uço~es") {
      word += "u"
    } else if (suffix === "ência" || suffix === "ências") {
      word += "ente"
    } else if (suffix === "mente") {
      alsoInR2(["ante", "avel", "ível"])
    } else if (suffix === "idade" || suffix === "idades") {
      alsoInR2(["abil", "ic", "iv"])
    } else if (!DELETE_IN_R2.has(suffix)) {
      // "iva", "ivo", "ivas", "ivos".
      alsoInR2(["at"])
    }
    return true
  }

  const verb = (): boolean => {
    const suffix = longest(word, VERB, rv)
    if (suffix === null) {
      return false
    }
    word = cut(suffix)
    return true
  }

  if (standard() || verb()) {
    if (word.endsWith("ci") && word.length - 1 >= rv) {
      word = word.slice(0, -1)
    }
  } else {
    const suffix = longest(word, RESIDUAL)
    if (suffix !== null && startOf(suffix) >= rv) {
      word = cut(suffix)
    }
  }

  const form = longest(word, FORM)
  if (form === "ç") {
    word = cut(form) + "c"
  } else if (form !== null && startOf(form) >= rv) {
    word = cut(form)
    if ((word.endsWith("gu") || word.endsWith("ci")) && word.length - 1 >= rv) {
      word = word.slice(0, -1)
    }
  }
  return word.replace(/a~/g, "ã").replace(/o~/g, "õ")
}
