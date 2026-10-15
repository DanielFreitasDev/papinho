import { describe, expect, it } from "vitest"

import { normalizeUsername, passwordSchema, usernameSchema } from "./schemas"

describe("usernameSchema", () => {
  it("normaliza @, maiúsculas e largura total (NFKC) como o servidor", () => {
    expect(normalizeUsername("  @Ana.Silva ")).toBe("ana.silva")
    expect(normalizeUsername("Ａｎａ")).toBe("ana")
    expect(usernameSchema.parse("@Bruno_99")).toBe("bruno_99")
  })

  it.each([
    "ab",
    "ana..silva",
    ".ana",
    "ana.",
    "ana-silva",
    "a".repeat(33),
    "ana silva",
  ])("recusa %s", (value) => {
    expect(usernameSchema.safeParse(value).success).toBe(false)
  })
})

describe("passwordSchema", () => {
  it("exige 12 caracteres contando emojis como um", () => {
    expect(passwordSchema.safeParse("curta123").success).toBe(false)
    expect(passwordSchema.safeParse("frase longa ok").success).toBe(true)
    expect(passwordSchema.safeParse("🙂".repeat(11)).success).toBe(false)
    expect(passwordSchema.safeParse("🙂".repeat(12)).success).toBe(true)
  })
})
