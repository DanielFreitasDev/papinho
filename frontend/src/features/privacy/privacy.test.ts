import { describe, expect, it } from "vitest"

import { blurAttributes, isPrivateModeShortcut } from "./private-mode"
import { matchesShortcut, shortcutFromEvent } from "./screen-lock"

const key = (
  code: string,
  modifiers: Partial<
    Record<"altKey" | "ctrlKey" | "shiftKey" | "metaKey", boolean>
  > = {}
) => ({
  code,
  altKey: false,
  ctrlKey: false,
  shiftKey: false,
  metaKey: false,
  ...modifiers,
})

describe("atalho do bloqueio de tela (PV-02)", () => {
  it("grava só combinações com modificador e letra ou número", () => {
    expect(
      shortcutFromEvent(key("KeyL", { altKey: true, shiftKey: true }))
    ).toBe("Alt+Shift+L")
    expect(shortcutFromEvent(key("Digit9", { ctrlKey: true }))).toBe("Ctrl+9")
    expect(shortcutFromEvent(key("KeyL"))).toBeNull()
    expect(shortcutFromEvent(key("Enter", { altKey: true }))).toBeNull()
  })

  it("reconhece o atalho exato, sem modificadores a mais", () => {
    expect(
      matchesShortcut(
        key("KeyL", { altKey: true, shiftKey: true }),
        "Alt+Shift+L"
      )
    ).toBe(true)
    expect(
      matchesShortcut(
        key("KeyL", { altKey: true, shiftKey: true, ctrlKey: true }),
        "Alt+Shift+L"
      )
    ).toBe(false)
    expect(matchesShortcut(key("Digit9", { ctrlKey: true }), "Ctrl+9")).toBe(
      true
    )
    expect(matchesShortcut(key("KeyK", { ctrlKey: true }), "Ctrl+9")).toBe(
      false
    )
  })
})

describe("modo privado (PV-01)", () => {
  const blur = {
    privateMode: true,
    messages: true,
    previews: false,
    names: true,
    photos: false,
    media: true,
    revealDelayMs: 1000,
  }

  it("lista só os itens ligados e o atraso suportado pelo CSS", () => {
    expect(blurAttributes(blur)).toEqual({
      blur: "messages names media",
      delay: "1000",
    })
    expect(blurAttributes({ ...blur, revealDelayMs: 1500 }).delay).toBe("2000")
  })

  it("não desfoca nada com o modo privado desligado ou sem itens", () => {
    expect(blurAttributes({ ...blur, privateMode: false }).blur).toBeNull()
    expect(
      blurAttributes({
        ...blur,
        messages: false,
        names: false,
        media: false,
      }).blur
    ).toBeNull()
    expect(blurAttributes(undefined).blur).toBeNull()
  })

  it("Alt+Shift+P alterna o modo privado", () => {
    expect(
      isPrivateModeShortcut(
        key("KeyP", { altKey: true, shiftKey: true }) as KeyboardEvent
      )
    ).toBe(true)
    expect(
      isPrivateModeShortcut(key("KeyP", { altKey: true }) as KeyboardEvent)
    ).toBe(false)
  })
})
