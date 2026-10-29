import { describe, expect, it } from "vitest"

import {
  filterMentions,
  matchesEveryone,
  mentionQuery,
  type MentionCandidate,
} from "./mention"

const person = (username: string, label: string): MentionCandidate => ({
  userId: username,
  username,
  label,
  hasPhoto: false,
  photoVersion: "",
})

describe("mentionQuery", () => {
  it("acha o @ no começo, depois de espaço e com o que já foi digitado", () => {
    expect(mentionQuery("@", 1)).toEqual({ from: 0, query: "" })
    expect(mentionQuery("oi @jo", 6)).toEqual({ from: 3, query: "jo" })
    expect(mentionQuery("oi\n@ana.s", 9)).toEqual({ from: 3, query: "ana.s" })
  })

  it("ignora e-mail, @ já fechado com espaço e cursor antes do @", () => {
    expect(mentionQuery("ana@papinho", 11)).toBeNull()
    expect(mentionQuery("@ana oi", 7)).toBeNull()
    expect(mentionQuery("oi @ana", 2)).toBeNull()
  })
})

describe("filterMentions", () => {
  const people = [person("jose.silva", "José Silva"), person("ana", "Ana")]

  it("filtra pelo começo do usuário ou de uma palavra do nome, sem acento", () => {
    expect(filterMentions(people, "")).toHaveLength(2)
    expect(filterMentions(people, "jos").map((p) => p.username)).toEqual([
      "jose.silva",
    ])
    expect(filterMentions(people, "Sil").map((p) => p.username)).toEqual([
      "jose.silva",
    ])
    expect(filterMentions(people, "xyz")).toEqual([])
  })

  it("@todos só enquanto for o começo dele", () => {
    expect(matchesEveryone("")).toBe(true)
    expect(matchesEveryone("To")).toBe(true)
    expect(matchesEveryone("ta")).toBe(false)
  })
})
