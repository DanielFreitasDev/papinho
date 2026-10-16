import { describe, expect, it } from "vitest"

import { tokenFromHash } from "./api"
import { resetPasswordSchema } from "./schemas"

describe("tokenFromHash", () => {
  it("lê o token do fragmento dos links de e-mail", () => {
    expect(tokenFromHash("#token=AbC_def-1234567890xyz")).toBe(
      "AbC_def-1234567890xyz"
    )
  })

  it.each(["", "#", "#token=", "#token=curto", "#token=<script>alert(1)"])(
    "recusa %s",
    (hash) => {
      expect(tokenFromHash(hash)).toBeNull()
    }
  )
})

describe("resetPasswordSchema", () => {
  it("exige 12 caracteres e a confirmação igual", () => {
    expect(
      resetPasswordSchema.safeParse({
        newPassword: "senha bem longa 1",
        confirmPassword: "senha bem longa 1",
      }).success
    ).toBe(true)
    expect(
      resetPasswordSchema.safeParse({
        newPassword: "senha bem longa 1",
        confirmPassword: "outra",
      }).success
    ).toBe(false)
    expect(
      resetPasswordSchema.safeParse({
        newPassword: "curta",
        confirmPassword: "curta",
      }).success
    ).toBe(false)
  })
})
