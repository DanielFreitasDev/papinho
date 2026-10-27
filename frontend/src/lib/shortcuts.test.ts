import { describe, expect, it } from "vitest"

import { matchShortcut, shortcutKeys } from "./shortcuts"

function key(
  code: string,
  { alt = false, shift = false, ctrl = true, altGraph = false } = {}
) {
  return {
    code,
    ctrlKey: ctrl,
    metaKey: false,
    altKey: alt,
    shiftKey: shift,
    repeat: false,
    isComposing: false,
    getModifierState: (name: string) => name === "AltGraph" && altGraph,
  }
}

describe("atalhos como os do WhatsApp", () => {
  it("reconhece pela tecla física e pelos modificadores exatos", () => {
    expect(matchShortcut(key("KeyU", { alt: true, shift: true }))).toBe(
      "markUnread"
    )
    expect(matchShortcut(key("KeyN", { alt: true }))).toBe("newChat")
    expect(matchShortcut(key("KeyN", { alt: true, shift: true }))).toBe(
      "newGroup"
    )
    expect(matchShortcut(key("KeyF", { shift: true }))).toBe("searchChat")
    expect(matchShortcut(key("KeyE", { alt: true }))).toBe("emoji")
  })

  it("não pega o AltGr de quem digita nem teclas sem Ctrl", () => {
    // No Windows, AltGr chega como Ctrl+Alt: AltGr+E digita "€" em vários teclados.
    expect(matchShortcut(key("KeyE", { alt: true, altGraph: true }))).toBeNull()
    expect(
      matchShortcut(key("KeyU", { alt: true, shift: true, ctrl: false }))
    ).toBeNull()
    expect(matchShortcut(key("KeyU", { alt: true }))).toBeNull()
  })

  it("mostra as teclas na ordem do WhatsApp", () => {
    expect(shortcutKeys("markUnread")).toEqual(["Ctrl", "Alt", "Shift", "U"])
    expect(shortcutKeys("searchChat")).toEqual(["Ctrl", "Shift", "F"])
  })
})
