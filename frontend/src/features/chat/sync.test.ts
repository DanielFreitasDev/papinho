import { QueryClient, QueryObserver } from "@tanstack/react-query"
import { describe, expect, it, vi } from "vitest"

import { apiFetch } from "@/lib/api-client"
import { chatKeys, syncConversation } from "./api"
import { fromHistory, type ConversationCache } from "./cache"
import type { Message, MessagePage } from "./types"

vi.mock("@/lib/api-client", () => ({ apiFetch: vi.fn() }))

function page(messages: Partial<Message>[], lastSeq: number): MessagePage {
  return {
    messages: messages as Message[],
    hasMore: false,
    lastSeq,
  } as MessagePage
}

describe("syncConversation", () => {
  it("aviso durante a primeira busca da conversa: espera e traz o que veio depois dela", async () => {
    const queryClient = new QueryClient()
    let release: (() => void) | undefined
    // A conversa aberta observa a consulta, como o componente faria; a primeira busca saiu antes da mensagem.
    const stop = new QueryObserver<ConversationCache>(queryClient, {
      queryKey: chatKeys.messages("c"),
      queryFn: () =>
        new Promise((resolve) => {
          release = () => resolve(fromHistory(page([], 0)))
        }),
    }).subscribe(() => undefined)
    vi.mocked(apiFetch).mockResolvedValueOnce(
      page([{ id: "m1", seq: 1, changeSeq: 1, body: "Leu isto?" }], 1)
    )

    const sync = syncConversation(queryClient, "c")
    release!()
    await sync

    const cache = queryClient.getQueryData<ConversationCache>(
      chatKeys.messages("c")
    )
    expect(vi.mocked(apiFetch)).toHaveBeenCalledWith(
      "/api/v1/conversations/c/messages?afterSeq=0&limit=500"
    )
    expect(cache?.messages.map((message) => message.body)).toEqual([
      "Leu isto?",
    ])
    stop()
  })

  it("não faz nada para conversa que não está aberta", async () => {
    const queryClient = new QueryClient()
    vi.mocked(apiFetch).mockClear()
    await syncConversation(queryClient, "fechada")
    expect(apiFetch).not.toHaveBeenCalled()
  })
})
