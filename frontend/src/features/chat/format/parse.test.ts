import { describe, expect, it } from "vitest"

import { firstLink, parseInline, parseMessage, plainText } from "./parse"

describe("formatação do WhatsApp (MS-01)", () => {
  it("reconhece negrito, itálico, riscado e mono", () => {
    expect(parseInline("*a* _b_ ~c~ `d`")).toEqual([
      { type: "bold", children: [{ type: "text", value: "a" }] },
      { type: "text", value: " " },
      { type: "italic", children: [{ type: "text", value: "b" }] },
      { type: "text", value: " " },
      { type: "strike", children: [{ type: "text", value: "c" }] },
      { type: "text", value: " " },
      { type: "code", value: "d" },
    ])
  })

  it("aninha marcadores e trata spoiler", () => {
    expect(parseInline("*_oi_* ||segredo||")).toEqual([
      {
        type: "bold",
        children: [
          { type: "italic", children: [{ type: "text", value: "oi" }] },
        ],
      },
      { type: "text", value: " " },
      { type: "spoiler", children: [{ type: "text", value: "segredo" }] },
    ])
  })

  it("não formata marcadores no meio de palavras nem com espaço por dentro", () => {
    expect(parseInline("2*3*4")).toEqual([{ type: "text", value: "2*3*4" }])
    expect(parseInline("snake_case_name")).toEqual([
      { type: "text", value: "snake_case_name" },
    ])
    expect(parseInline("* nada *")).toEqual([
      { type: "text", value: "* nada *" },
    ])
    expect(parseInline("**")).toEqual([{ type: "text", value: "**" }])
  })

  it("aceita pontuação em volta dos marcadores", () => {
    expect(parseInline("(*sim*).")).toEqual([
      { type: "text", value: "(" },
      { type: "bold", children: [{ type: "text", value: "sim" }] },
      { type: "text", value: ")." },
    ])
  })

  it("conteúdo de mono é literal", () => {
    expect(parseInline("`*não* negrito`")).toEqual([
      { type: "code", value: "*não* negrito" },
    ])
  })

  it("detecta links sem a pontuação final e menções", () => {
    expect(
      parseInline("veja https://exemplo.com/a?b=1. @Ana.Silva, oi")
    ).toEqual([
      { type: "text", value: "veja " },
      { type: "link", href: "https://exemplo.com/a?b=1" },
      { type: "text", value: ". " },
      { type: "mention", username: "ana.silva" },
      { type: "text", value: ", oi" },
    ])
    expect(parseInline("email@exemplo.com")).toEqual([
      { type: "text", value: "email@exemplo.com" },
    ])
  })

  it("texto com HTML continua texto", () => {
    expect(parseInline('<img src=x onerror="alert(1)">')).toEqual([
      { type: "text", value: '<img src=x onerror="alert(1)">' },
    ])
  })

  it("monta blocos: código com linguagem, citação e listas", () => {
    const blocks = parseMessage(
      [
        "Olha:",
        "```ts",
        "const a = 1",
        "```",
        "> citado *forte*",
        "> segunda",
        "- um",
        "- dois",
        "3. três",
        "4. quatro",
      ].join("\n")
    )
    expect(blocks).toEqual([
      { type: "paragraph", children: [{ type: "text", value: "Olha:" }] },
      { type: "code", language: "ts", value: "const a = 1" },
      {
        type: "quote",
        children: [
          {
            type: "paragraph",
            children: [
              { type: "text", value: "citado " },
              { type: "bold", children: [{ type: "text", value: "forte" }] },
              { type: "break" },
              { type: "text", value: "segunda" },
            ],
          },
        ],
      },
      {
        type: "list",
        ordered: false,
        start: 1,
        items: [
          [{ type: "text", value: "um" }],
          [{ type: "text", value: "dois" }],
        ],
      },
      {
        type: "list",
        ordered: true,
        start: 3,
        items: [
          [{ type: "text", value: "três" }],
          [{ type: "text", value: "quatro" }],
        ],
      },
    ])
  })

  it("``` numa linha só vira bloco mono; sem fechamento fica texto", () => {
    expect(parseMessage("```mono```")).toEqual([
      { type: "code", language: null, value: "mono" },
    ])
    expect(parseMessage("```sem fim")).toEqual([
      {
        type: "paragraph",
        children: [{ type: "text", value: "```sem fim" }],
      },
    ])
  })

  it("texto puro para prévias esconde o spoiler", () => {
    expect(plainText("*Oi* ||surpresa|| `x`")).toBe("Oi ▒▒▒ x")
  })

  it("acha o primeiro link fora de código (prévia, MD-04)", () => {
    expect(firstLink("veja https://a.com/x. e https://b.com")).toBe(
      "https://a.com/x"
    )
    expect(firstLink("```\nhttps://code.com\n```\n> https://q.com")).toBe(
      "https://q.com"
    )
    expect(firstLink("sem link")).toBeNull()
  })
})
