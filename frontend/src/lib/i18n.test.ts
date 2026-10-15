import { describe, expect, it } from "vitest"

import en from "@/locales/en.json"
import ptBR from "@/locales/pt-BR.json"

function keys(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null) {
    return [prefix]
  }
  return Object.entries(value).flatMap(([key, child]) =>
    keys(child, prefix ? `${prefix}.${key}` : key)
  )
}

describe("i18n", () => {
  it("en possui exatamente as mesmas chaves que pt-BR", () => {
    expect(keys(en).sort()).toEqual(keys(ptBR).sort())
  })
})
