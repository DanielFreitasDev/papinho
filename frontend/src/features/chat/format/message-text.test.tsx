import { fireEvent, render, waitFor } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { MessageText } from "./message-text"

/** Elementos ou atributos que só existiriam se o HTML do usuário virasse DOM. */
function injected(container: HTMLElement) {
  const found: string[] = []
  container
    .querySelectorAll("img, script, svg, iframe, object, embed, style, b, i")
    .forEach((element) => found.push(element.tagName.toLowerCase()))
  container.querySelectorAll("*").forEach((element) => {
    for (const attribute of element.getAttributeNames()) {
      if (attribute.startsWith("on")) {
        found.push(`${element.tagName.toLowerCase()}[${attribute}]`)
      }
    }
  })
  container.querySelectorAll("a").forEach((anchor) => {
    if (!/^https?:\/\//.test(anchor.getAttribute("href") ?? "")) {
      found.push(`a[href=${anchor.getAttribute("href")}]`)
    }
  })
  return found
}

const PAYLOADS = [
  `<img src=x onerror="alert(1)">`,
  `<script>alert(1)</script>`,
  `<svg onload="alert(1)"><circle r="5"/></svg>`,
  `<a href="javascript:alert(1)">clique</a>`,
  `javascript:alert(1)`,
  `<iframe src="https://exemplo.com"></iframe>`,
  `<b>negrito</b> <i>itálico</i> <style>*{color:red}</style>`,
]

/** MS-01: o corpo da mensagem é texto; HTML do usuário nunca vira elemento, em nenhum lugar da formatação. */
describe("MessageText", () => {
  it.each(PAYLOADS)("mostra como texto: %s", (payload) => {
    const { container } = render(<MessageText text={payload} />)
    expect(injected(container)).toEqual([])
    expect(container.textContent).toContain(payload)
  })

  it("não injeta HTML dentro de negrito, citação, lista, spoiler e código", async () => {
    const img = `<img src=x onerror="alert(1)">`
    const text = [
      `*${img}* _${img}_ ~${img}~ \`${img}\``,
      `> ${img}`,
      `- ${img}`,
      `1. ${img}`,
      `||${img}||`,
      "```html",
      `${img}<script>alert(1)</script>`,
      "```",
    ].join("\n")
    const { container, getByRole } = render(<MessageText text={text} />)
    expect(
      container.querySelector("strong, em, s, blockquote, ul, ol")
    ).not.toBeNull()
    fireEvent.click(getByRole("button"))
    // O bloco com linguagem é destacado depois do import dinâmico do lowlight.
    await waitFor(() =>
      expect(container.querySelector("pre code span")).not.toBeNull()
    )
    expect(injected(container)).toEqual([])
    expect(container.textContent).toContain(`${img}<script>alert(1)</script>`)
  })

  it("link só com http(s), aberto em outra aba e sem referrer", () => {
    const { container } = render(
      <MessageText text="veja https://exemplo.com/a?b=1 e javascript:alert(1)" />
    )
    const anchors = container.querySelectorAll("a")
    expect(anchors).toHaveLength(1)
    expect(anchors[0].getAttribute("href")).toBe("https://exemplo.com/a?b=1")
    expect(anchors[0].getAttribute("rel")).toContain("noopener")
    expect(anchors[0].getAttribute("target")).toBe("_blank")
  })
})
