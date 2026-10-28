import { describe, expect, it } from "vitest"

import {
  activeFormats,
  analyze,
  applyFormat,
  canFormat,
  segments,
  type Edit,
  type Format,
} from "./compose"
import { parseMessage } from "./parse"

/** Aplica a edição como o campo faz e devolve o texto novo e a seleção. */
function apply(text: string, start: number, end: number, format: Format) {
  const edit = applyFormat(analyze(text), start, end, format)
  if (!edit) {
    return null
  }
  return { text: run(text, edit), selection: edit.selection }
}

function run(text: string, edit: Edit) {
  return text.slice(0, edit.from) + edit.insert + text.slice(edit.to)
}

function select(text: string, part: string) {
  const start = text.indexOf(part)
  return [start, start + part.length] as const
}

function active(text: string, start: number, end = start) {
  return [...activeFormats(analyze(text), start, end)].sort()
}

describe("aparência do campo (segments)", () => {
  it("marca o conteúdo e apaga os marcadores", () => {
    expect(segments("a *b* c")).toEqual([
      { from: 0, to: 2, looks: [] },
      { from: 2, to: 3, looks: ["marker"] },
      { from: 3, to: 4, looks: ["bold"] },
      { from: 4, to: 5, looks: ["marker"] },
      { from: 5, to: 7, looks: [] },
    ])
  })

  it("cobre o texto inteiro, em ordem, também com blocos e aninhados", () => {
    for (const text of [
      "",
      "> citação com *negrito*\n- item _um_\n1. item ~dois~",
      "```js\nconst x = 1\n```\ndepois `mono` e ||spoiler||",
      "*_aninhado_* https://exemplo.com @ana.silva",
      "linha\n\n\nfim\n",
    ]) {
      const list = segments(text)
      expect(
        list.map((segment) => text.slice(segment.from, segment.to)).join("")
      ).toBe(text)
      list.forEach((segment, index) => {
        expect(segment.to).toBeGreaterThan(segment.from)
        if (index > 0) {
          expect(segment.from).toBe(list[index - 1].to)
        }
      })
    }
  })

  it("bloco de código, citação, lista, link e menção", () => {
    const text = "```\nx\n```\n> y\n- z https://a.b @ana.b"
    const looksOf = (part: string) =>
      segments(text).find(
        (segment) => text.slice(segment.from, segment.to) === part
      )?.looks
    expect(looksOf("```\n")).toBeUndefined()
    expect(looksOf("x")).toEqual(["codeBlock"])
    expect(looksOf("> ")).toEqual(["marker"])
    expect(looksOf("- ")).toEqual(["marker"])
    expect(looksOf("https://a.b")).toEqual(["link"])
    expect(looksOf("@ana.b")).toEqual(["mention"])
  })
})

describe("o que já está aplicado (activeFormats)", () => {
  it("negrito pela seleção, com ou sem os marcadores, e pelo cursor", () => {
    expect(active("*abc*", 1, 4)).toEqual(["bold"])
    expect(active("*abc*", 0, 5)).toEqual(["bold"])
    expect(active("*abc*", 2)).toEqual(["bold"])
    expect(active("*abc*", 0)).toEqual([])
    expect(active("*abc* d", 6)).toEqual([])
    expect(active("*abc* d", 0, 7)).toEqual([])
  })

  it("aninhados e blocos", () => {
    expect(active("*_oi_*", 2, 4)).toEqual(["bold", "italic"])
    expect(active("> a\n> b", 0, 7)).toEqual(["quote"])
    expect(active("- a\n- b", 0, 7)).toEqual(["bulletList"])
    expect(active("1. a\n2. b", 0, 9)).toEqual(["orderedList"])
    expect(active("```\ncódigo\n```", 5)).toEqual(["codeBlock"])
  })

  it("dentro de código, só o próprio código", () => {
    const code = analyze("`abc`")
    expect(canFormat(code, 1, 4, "bold")).toBe(false)
    expect(canFormat(code, 1, 4, "code")).toBe(true)
    const block = analyze("```\nabc\n```")
    expect(canFormat(block, 4, 7, "italic")).toBe(false)
    expect(canFormat(block, 4, 7, "quote")).toBe(false)
    expect(canFormat(block, 4, 7, "codeBlock")).toBe(true)
  })
})

describe("o que cada botão faz (applyFormat)", () => {
  it("marca a seleção e deixa o conteúdo selecionado", () => {
    const text = "olá mundo"
    expect(apply(text, ...select(text, "mundo"), "bold")).toEqual({
      text: "olá *mundo*",
      selection: [5, 10],
    })
  })

  it("pega a palavra inteira e deixa os espaços de fora", () => {
    expect(apply("fantástico", 3, 7, "bold")?.text).toBe("*fantástico*")
    expect(apply("uma palavra aqui", 4, 12, "italic")?.text).toBe(
      "uma _palavra_ aqui"
    )
    expect(apply("um dois", 4, 4, "strike")?.text).toBe("um ~dois~")
  })

  it("sem palavra, põe o par com o cursor no meio; de novo, tira", () => {
    const first = apply("oi ", 3, 3, "bold")!
    expect(first).toEqual({ text: "oi **", selection: [4, 4] })
    expect(apply(first.text, 4, 4, "bold")).toEqual({
      text: "oi ",
      selection: [3, 3],
    })
    expect(apply("", 0, 0, "spoiler")).toEqual({
      text: "||||",
      selection: [2, 2],
    })
  })

  it("desliga o que já estava aplicado", () => {
    expect(apply("*abc*", 1, 4, "bold")).toEqual({
      text: "abc",
      selection: [0, 3],
    })
    expect(apply("um *dois* três", 5, 5, "bold")?.text).toBe("um dois três")
  })

  it("absorve o mesmo formato em vez de aninhar", () => {
    expect(apply("a *b* c", 0, 7, "bold")?.text).toBe("*a b c*")
    expect(apply("*a* b", 1, 5, "bold")?.text).toBe("*a b*")
  })

  it("em várias linhas, marca cada linha e respeita listas e citações", () => {
    expect(apply("um\ndois", 0, 7, "bold")?.text).toBe("*um*\n*dois*")
    expect(apply("- a\n- b", 0, 7, "bold")?.text).toBe("- *a*\n- *b*")
    expect(apply("> a b", 0, 5, "strike")?.text).toBe("> ~a b~")
  })

  it("aninha formatos diferentes", () => {
    const bold = apply("abc", 0, 3, "bold")!
    const both = apply(bold.text, ...bold.selection, "italic")!
    expect(both.text).toBe("*_abc_*")
    expect(active(both.text, ...both.selection)).toEqual(["bold", "italic"])
  })

  it("código e spoiler", () => {
    expect(apply("x = 1", 0, 5, "code")?.text).toBe("`x = 1`")
    expect(apply("fim", 0, 3, "spoiler")?.text).toBe("||fim||")
    expect(apply("`abc`", 1, 4, "bold")).toBeNull()
  })

  it("citação e listas por linha, de ida e de volta", () => {
    const quoted = apply("a\nb", 0, 3, "quote")!
    expect(quoted.text).toBe("> a\n> b")
    expect(apply(quoted.text, 0, quoted.text.length, "quote")?.text).toBe(
      "a\nb"
    )
    const bullets = apply("a\nb", 0, 3, "bulletList")!
    expect(bullets.text).toBe("- a\n- b")
    const numbered = apply(bullets.text, 0, 7, "orderedList")!
    expect(numbered.text).toBe("1. a\n2. b")
    expect(apply(numbered.text, 0, 9, "orderedList")?.text).toBe("a\nb")
  })

  it("lista numerada continua a contagem e linha vazia ganha o marcador", () => {
    expect(apply("1. a\nb", 6, 6, "orderedList")?.text).toBe("1. a\n2. b")
    expect(apply("", 0, 0, "bulletList")).toEqual({
      text: "- ",
      selection: [2, 2],
    })
    expect(apply("a\n\nb", 0, 4, "bulletList")?.text).toBe("- a\n\n- b")
  })

  it("bloco de código em linhas inteiras, de ida e de volta", () => {
    const block = apply("x = 1", 2, 2, "codeBlock")!
    expect(block).toEqual({ text: "```\nx = 1\n```", selection: [4, 9] })
    expect(apply(block.text, 5, 5, "codeBlock")).toEqual({
      text: "x = 1",
      selection: [0, 5],
    })
  })

  it("o resultado é o que a bolha mostra", () => {
    const text = "olá mundo"
    const bold = apply(text, ...select(text, "mundo"), "bold")!
    expect(parseMessage(bold.text)).toEqual([
      {
        type: "paragraph",
        children: [
          { type: "text", value: "olá " },
          { type: "bold", children: [{ type: "text", value: "mundo" }] },
        ],
      },
    ])
    const list = apply("a\nb", 0, 3, "orderedList")!
    expect(parseMessage(list.text)[0]).toMatchObject({
      type: "list",
      ordered: true,
    })
  })
})
