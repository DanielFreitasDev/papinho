import { afterEach, describe, expect, it, vi } from "vitest"

import { installRipple } from "./ripple"

function press(target: Element, x = 10, y = 5) {
  target.dispatchEvent(
    new MouseEvent("pointerdown", {
      bubbles: true,
      button: 0,
      clientX: x,
      clientY: y,
    })
  )
}

/** Botão com data-ripple (e um filho, para tocar nele) de 40 × 20 px. */
function host({ disabled = false } = {}) {
  const element = document.createElement("button")
  element.dataset.ripple = ""
  element.disabled = disabled
  element.append(document.createElement("span"))
  element.getBoundingClientRect = () => new DOMRect(0, 0, 40, 20)
  document.body.replaceChildren(element)
  return element
}

describe("onda de toque", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.body.replaceChildren()
  })

  it("cresce a partir do ponto tocado, também tocando num filho", () => {
    installRipple()
    const button = host()
    press(button.querySelector("span")!, 10, 5)
    expect(button).toHaveAttribute("data-rippling")
    expect(button.style.getPropertyValue("--ripple-x")).toBe("10px")
    expect(button.style.getPropertyValue("--ripple-y")).toBe("5px")
    // O diâmetro cobre do ponto até o canto mais longe (30 × 15).
    expect(
      Number.parseFloat(button.style.getPropertyValue("--ripple-size"))
    ).toBeCloseTo(2 * Math.hypot(30, 15))
  })

  it("não acontece em elemento desabilitado nem com movimento reduzido", () => {
    installRipple()
    const disabled = host({ disabled: true })
    press(disabled)
    expect(disabled).not.toHaveAttribute("data-rippling")

    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: query.includes("reduce"),
    }))
    const button = host()
    press(button)
    expect(button).not.toHaveAttribute("data-rippling")
  })
})
