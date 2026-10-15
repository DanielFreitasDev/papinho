import { afterEach, describe, expect, it } from "vitest"

import {
  normalize,
  pushRecent,
  readRecent,
  searchEmojis,
  type EmojiItem,
} from "./data"

const emojis: EmojiItem[] = [
  {
    unicode: "❤️",
    label: "coração vermelho",
    tags: ["amor", "coração"],
    group: 8,
    order: 1,
  },
  {
    unicode: "🍕",
    label: "pizza",
    tags: ["queijo", "fatia"],
    group: 4,
    order: 2,
  },
  {
    unicode: "😂",
    label: "rosto chorando de rir",
    tags: ["lágrima", "riso"],
    group: 0,
    order: 3,
  },
]

describe("seletor de emojis (MS-10)", () => {
  afterEach(() => localStorage.clear())

  it("busca sem acento nem caixa no nome e nas etiquetas", () => {
    expect(normalize("Coração")).toBe("coracao")
    expect(searchEmojis(emojis, "CORACAO").map((e) => e.unicode)).toEqual([
      "❤️",
    ])
    expect(searchEmojis(emojis, "lagri").map((e) => e.unicode)).toEqual(["😂"])
    expect(searchEmojis(emojis, "  ")).toEqual([])
  })

  it("recentes ficam por pessoa, sem repetir, o último primeiro", () => {
    pushRecent("ana", "🍕")
    pushRecent("ana", "❤️")
    pushRecent("ana", "🍕")
    expect(readRecent("ana")).toEqual(["🍕", "❤️"])
    expect(readRecent("bruno")).toEqual([])
  })
})
