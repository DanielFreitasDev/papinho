import { describe, expect, it } from "vitest"

import fixture from "./stem-pt.fixture.tsv?raw"
import { fold, lexeme } from "./search-match"
import { stemPortuguese } from "./stem-pt"

/** Radicais como o servidor indexa a palavra: separada como o PostgreSQL separa ("x²" é "x"). */
function lexemes(word: string) {
  return (fold(word).folded.match(/[\p{L}\p{Nd}]+/gu) ?? [])
    .map(lexeme)
    .filter((stem) => stem !== null)
    .join(" ")
}

describe("stemmer português (o mesmo do PostgreSQL)", () => {
  it("dá o radical que o PostgreSQL deu para cada palavra da fixture", () => {
    const rows = fixture
      .split("\n")
      .filter((line) => line && !line.startsWith("#"))
      .map((line) => line.split("\t"))
    expect(rows.length).toBeGreaterThan(5000)
    const different = rows
      .filter(([word, expected = ""]) => lexemes(word) !== expected)
      .map(([word, expected]) => `${word}: ${expected} ≠ ${lexemes(word)}`)
    expect(different).toEqual([])
  })

  // Valores do ts_lexize('portuguese_stem', ...) do PostgreSQL 18; com acento valem as regras acentuadas.
  it.each([
    ["mensagem", "mensag"],
    ["falando", "fal"],
    ["fronteira", "fronteir"],
    ["biologia", "biolog"],
    ["felizmente", "feliz"],
    ["rapidamente", "rapid"],
    ["presidencia", "presidenc"],
    ["presidência", "president"],
    ["reuniao", "reunia"],
    ["cidade", "cidad"],
  ])("%s → %s", (word, stem) => {
    expect(stemPortuguese(word)).toBe(stem)
  })
})
