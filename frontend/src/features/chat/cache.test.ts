import { describe, expect, it } from "vitest"

import {
  addPending,
  applyChanges,
  confirmSent,
  fromHistory,
  prependOlder,
  removeMessages,
} from "./cache"
import type { Message } from "./types"

function message(seq: number, changeSeq = seq, body = `m${seq}`): Message {
  return {
    id: `id-${seq}`,
    conversationId: "c",
    seq,
    changeSeq,
    senderId: "ana",
    clientMessageId: `client-${seq}`,
    body,
    createdAt: "2026-10-15T12:00:00Z",
    replyTo: null,
    forwardCount: 0,
    forwardedMany: false,
    pinnedAt: null,
    reactions: [],
    starred: false,
    attachment: null,
    editedAt: null,
    deleted: false,
    kind: "USER",
  }
}

describe("cache da conversa (RT-01, RT-02)", () => {
  it("histórico define o cursor pelo lastSeq e ordena por seq", () => {
    const cache = fromHistory({
      messages: [message(5), message(4)],
      lastSeq: 7,
      hasMore: true,
    })
    expect(cache.messages.map((m) => m.seq)).toEqual([4, 5])
    expect(cache.cursor).toBe(7)
  })

  it("afterSeq substitui alteradas, acrescenta novas e não duplica", () => {
    const cache = fromHistory({
      messages: [message(1), message(2)],
      lastSeq: 2,
      hasMore: false,
    })
    const next = applyChanges(cache, {
      messages: [message(1, 3, "reagida"), message(3, 4)],
      lastSeq: 4,
      hasMore: false,
    })
    expect(next.messages.map((m) => [m.seq, m.body])).toEqual([
      [1, "reagida"],
      [2, "m2"],
      [3, "m3"],
    ])
    expect(next.cursor).toBe(4)
    expect(
      applyChanges(next, {
        messages: [message(3, 4)],
        lastSeq: 4,
        hasMore: false,
      }).messages
    ).toHaveLength(3)
  })

  it("ignora mudança em mensagem mais antiga que a janela carregada", () => {
    const cache = fromHistory({
      messages: [message(10)],
      lastSeq: 10,
      hasMore: true,
    })
    const next = applyChanges(cache, {
      messages: [message(2, 11)],
      lastSeq: 11,
      hasMore: false,
    })
    expect(next.messages.map((m) => m.seq)).toEqual([10])
    expect(next.cursor).toBe(11)
  })

  it("com hasMore o cursor para no último changeSeq recebido", () => {
    const cache = fromHistory({ messages: [], lastSeq: 0, hasMore: false })
    const next = applyChanges(cache, {
      messages: [message(1), message(2)],
      lastSeq: 9,
      hasMore: true,
    })
    expect(next.cursor).toBe(2)
  })

  it("pendente some quando a mensagem com o mesmo clientMessageId chega", () => {
    let cache = fromHistory({ messages: [], lastSeq: 0, hasMore: false })
    cache = addPending(cache, {
      clientMessageId: "client-1",
      conversationId: "c",
      body: "m1",
      replyTo: null,
      createdAt: "2026-10-15T12:00:00Z",
      status: "sending",
      attachment: null,
      attachmentId: null,
    })
    expect(cache.pending).toHaveLength(1)
    cache = confirmSent(cache, message(1))
    expect(cache.pending).toHaveLength(0)
    expect(cache.messages).toHaveLength(1)
  })

  it("página antiga entra antes, sem repetir", () => {
    const cache = fromHistory({
      messages: [message(3), message(4)],
      lastSeq: 4,
      hasMore: true,
    })
    const next = prependOlder(cache, {
      messages: [message(1), message(2), message(3)],
      lastSeq: 4,
      hasMore: false,
    })
    expect(next.messages.map((m) => m.seq)).toEqual([1, 2, 3, 4])
    expect(next.hasOlder).toBe(false)
  })

  it("apagar para mim tira a mensagem sem mexer no cursor (MS-06)", () => {
    const cache = fromHistory({
      messages: [message(1), message(2), message(3)],
      lastSeq: 3,
      hasMore: false,
    })
    const next = removeMessages(cache, ["id-2"])
    expect(next.messages.map((m) => m.seq)).toEqual([1, 3])
    expect(next.cursor).toBe(3)
  })

  it("apagada para todos substitui a original pelo aviso (MS-06)", () => {
    const cache = fromHistory({
      messages: [message(1), message(2)],
      lastSeq: 2,
      hasMore: false,
    })
    const next = applyChanges(cache, {
      messages: [{ ...message(1, 3, ""), deleted: true }],
      lastSeq: 3,
      hasMore: false,
    })
    expect(next.messages[0]).toMatchObject({ body: "", deleted: true })
    expect(next.cursor).toBe(3)
  })
})
