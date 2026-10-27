import { describe, expect, it } from "vitest"

import { MEMBER_COLORS, memberColor } from "./member-color"

describe("cor do nome no grupo", () => {
  it("é sempre a mesma para a mesma pessoa e fica entre as cores do CSS", () => {
    const id = "01a17d4f-b352-7896-9111-7d5c8e62304d"
    expect(memberColor(id)).toBe(memberColor(id))
    for (let index = 0; index < 200; index++) {
      const color = memberColor(crypto.randomUUID())
      expect(color).toBeGreaterThanOrEqual(0)
      expect(color).toBeLessThan(MEMBER_COLORS)
    }
  })

  it("espalha pessoas diferentes pelas cores", () => {
    const used = new Set(
      Array.from({ length: 60 }, () => memberColor(crypto.randomUUID()))
    )
    expect(used.size).toBeGreaterThan(MEMBER_COLORS / 2)
  })
})
