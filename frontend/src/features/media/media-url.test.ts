import { describe, expect, it } from "vitest"

import { mediaUrl } from "./api"

describe("mediaUrl", () => {
  it("leva o grant do anexo de conversa trancada na URL (PV-03)", () => {
    expect(mediaUrl("a1")).toBe("/api/v1/media/a1/content")
    expect(mediaUrl({ id: "a1", grant: null }, "thumbnail")).toBe(
      "/api/v1/media/a1/thumbnail"
    )
    expect(mediaUrl({ id: "a1", grant: "123.x-y_z" })).toBe(
      "/api/v1/media/a1/content?grant=123.x-y_z"
    )
    expect(mediaUrl({ id: "a1", grant: "123.x" }, "content", true)).toBe(
      "/api/v1/media/a1/content?download=true&grant=123.x"
    )
  })
})
