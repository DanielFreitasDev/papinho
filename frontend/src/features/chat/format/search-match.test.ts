import { describe, expect, it } from "vitest"

import { excerpt, findMatches, searchTerms } from "./search-match"

/** Os trechos destacados, como texto. */
function marked(text: string, query: string) {
  const terms = searchTerms(query)
  return terms
    ? findMatches(text, terms).map(([start, end]) => text.slice(start, end))
    : []
}

describe("destaque da busca (mesma regra do servidor, GR-06)", () => {
  it("não diferencia acentos nem maiúsculas", () => {
    expect(marked("A Ação de amanhã", "acao")).toEqual(["Ação"])
    expect(marked("sem acao nenhuma", "AÇÃO")).toEqual(["acao"])
    expect(marked("Café e CAFÉ", "cafe")).toEqual(["Café", "CAFÉ"])
  })

  it("acha a mesma palavra no plural e com o mesmo radical, como a busca em português", () => {
    expect(marked("As reuniões de hoje", "reunião")).toEqual(["reuniões"])
    expect(marked("Uma mensagem só", "mensagens")).toEqual(["mensagem"])
    expect(marked("Estou falando com ela", "falar")).toEqual(["falando"])
  })

  it("acha o trecho digitado no meio da palavra", () => {
    expect(marked("Chegamos a Fortaleza", "taleza")).toEqual(["taleza"])
    expect(marked("bom dia, Bom Dia", "bom dia")).toEqual([
      "bom dia",
      "Bom Dia",
    ])
    // Trecho que é o radical da palavra: a busca acha pelos dois caminhos e a palavra inteira fica marcada.
    expect(marked("Chegamos a Fortaleza", "fortal")).toEqual(["Fortaleza"])
  })

  it("palavra vazia não é destacada sozinha; a frase com ela, sim", () => {
    expect(marked("o carro de Ana", "carro de")).toEqual(["carro de"])
    expect(marked("de lá para cá, de novo", "de x")).toEqual([])
  })

  it("segue a sintaxe da busca: palavra com - fica de fora, or é operador", () => {
    expect(marked("praia e serra", "praia -serra")).toEqual(["praia"])
    expect(marked("praia ou serra", "praia or serra")).toEqual([
      "praia",
      "serra",
    ])
  })

  it("caracteres especiais da busca são texto, sem quebrar nada", () => {
    expect(marked("custa (a+b)*2 [ok]?", "(a+b)*2")).toEqual(["(a+b)*2"])
    expect(marked("ponto.com.br", ".com")).toEqual([".com"])
  })

  it("as posições valem para o texto original, com emoji e letra decomposta", () => {
    const text = "👍 Ação 👍 acao"
    expect(marked(text, "ação")).toEqual(["Ação", "acao"])
    // "a" + acento combinante (NFD) conta como "á".
    expect(marked("mágica", "magica")).toEqual(["mágica"])
    expect(marked("Straße", "strasse")).toEqual(["Straße"])
  })

  it("busca vazia ou de uma letra não destaca nada solto", () => {
    expect(searchTerms("   ")).toBeNull()
    expect(marked("a casa amarela", "a")).toEqual([])
  })

  it("o resultado recortado começa perto do termo, numa palavra inteira", () => {
    const terms = searchTerms("reunião")!
    const long =
      "Lembrete do condomínio para todos os moradores do bloco B: reunião às 19h"
    expect(excerpt(long, terms)).toBe(
      "…os moradores do bloco B: reunião às 19h"
    )
    expect(
      excerpt("😀".repeat(20) + " " + "x".repeat(30) + " reunião", terms)
    ).toMatch(/^…x+ reunião$/)
    expect(excerpt("reunião às 19h", terms)).toBe("reunião às 19h")
  })
})
