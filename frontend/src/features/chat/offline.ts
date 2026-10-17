import { useEffect } from "react"
import { useQueryClient, type QueryClient } from "@tanstack/react-query"

import { ApiError, apiFetch } from "@/lib/api-client"
import { onLogout } from "@/lib/logout-cleanup"
import {
  clearOfflineData,
  dequeue,
  forgetConversation,
  offlineDb,
  openOfflineDb,
  writeSnapshot,
} from "@/lib/offline-db"
import { realtime } from "@/lib/realtime"
import { refreshQueries } from "@/lib/query-refresh"
import { chatKeys } from "./api"
import { confirmSent, markPendingFailed, type ConversationCache } from "./cache"
import type { Conversation, Message } from "./types"

/** Mensagens guardadas por conversa: as mais recentes. */
const KEEP = 100

onLogout(clearOfflineData)

/**
 * Conversas recentes offline (NT-05): guarda a lista, os dados e as últimas mensagens de cada conversa que
 * aparece na lista principal; as consultas usam isso só quando a rede falha (rede primeiro). As trancadas não
 * estão nela, então nunca são guardadas, e a que for trancada depois de guardada sai do aparelho (PV-03). Textos
 * escritos sem conexão saem quando ela volta.
 */
export function useOfflineChat(meId: string, me: unknown) {
  const queryClient = useQueryClient()
  useEffect(() => {
    let stopped = false
    let unsubscribe = () => {}
    void (async () => {
      await openOfflineDb(meId)
      if (stopped) {
        return
      }
      await writeSnapshot("me", me)
      unsubscribe = persist(queryClient)
      void flushOutbox(queryClient)
    })()
    const offState = realtime.onState((state) => {
      if (state === "connected") {
        void flushOutbox(queryClient)
      }
    })
    const online = () => void flushOutbox(queryClient)
    window.addEventListener("online", online)
    return () => {
      stopped = true
      unsubscribe()
      offState()
      window.removeEventListener("online", online)
    }
  }, [meId, me, queryClient])
}

function persist(queryClient: QueryClient) {
  // Trancada (PV-03) nunca vai para o aparelho: só conversas da lista principal ou com dados não trancados.
  const storable = (id: string) => {
    const detail = queryClient.getQueryData<Conversation>(chatKeys.detail(id))
    if (detail) {
      return !detail.locked
    }
    return (
      queryClient.getQueryData<Conversation[]>(chatKeys.list(false)) ?? []
    ).some((conversation) => conversation.id === id)
  }
  const timers = new Map<string, number>()
  const later = (key: string, write: () => void) => {
    window.clearTimeout(timers.get(key))
    timers.set(key, window.setTimeout(write, 500))
  }
  // Trancada depois de guardada (aqui ou em outro aparelho): o que já estava no aparelho sai, com as gravações
  // ainda agendadas e os textos na fila.
  const forget = (id: string) => {
    for (const key of [`detail:${id}`, `messages:${id}`]) {
      window.clearTimeout(timers.get(key))
      timers.delete(key)
    }
    void forgetConversation(id)
  }
  const save = (key: readonly unknown[], data: unknown) => {
    if (data === undefined) {
      return
    }
    if (key[0] === "conversations" && key[1] === "list" && key[2] === false) {
      later("list", () => void writeSnapshot("list", data))
    } else if (key[0] === "conversations" && key[1] === "locked") {
      ;(data as Conversation[]).forEach((conversation) =>
        forget(conversation.id)
      )
    } else if (key[0] === "conversations" && key[1] === "detail") {
      const id = String(key[2])
      if ((data as Conversation).locked) {
        forget(id)
      } else {
        later(`detail:${id}`, () => void writeSnapshot(`detail:${id}`, data))
      }
    } else if (key[0] === "messages" && key.length === 2) {
      const id = String(key[1])
      if (!storable(id)) {
        return
      }
      later(`messages:${id}`, () => {
        const cache = data as ConversationCache
        const recent = cache.messages.slice(-KEEP)
        void writeSnapshot(`messages:${id}`, {
          ...cache,
          messages: recent,
          pending: [],
          hasOlder: cache.hasOlder || recent.length < cache.messages.length,
        } satisfies ConversationCache)
      })
    }
  }
  // O que já carregou antes de o banco abrir também é guardado.
  queryClient
    .getQueryCache()
    .getAll()
    .forEach((query) => save(query.queryKey, query.state.data))
  return queryClient.getQueryCache().subscribe((event) => {
    if (event.type === "updated" && event.action.type === "success") {
      save(event.query.queryKey, event.query.state.data)
    }
  })
}

let flushing = false

/**
 * Envia a fila em ordem, com o mesmo clientMessageId: se o primeiro envio chegou ao servidor e só a resposta se
 * perdeu, o reenvio devolve a mesma mensagem (RT-02). Erro de rede para tudo até a próxima conexão; recusa do
 * servidor marca a mensagem como falha (com "tentar de novo").
 */
export async function flushOutbox(queryClient: QueryClient) {
  const db = offlineDb()
  if (!db || flushing) {
    return
  }
  flushing = true
  try {
    const queued = await db.outbox.orderBy("createdAt").toArray()
    for (const item of queued) {
      try {
        const message = await apiFetch<Message>(
          `/api/v1/conversations/${item.conversationId}/messages`,
          {
            method: "POST",
            json: {
              clientMessageId: item.clientMessageId,
              body: item.body,
              replyToId: item.replyToId,
              silent: item.silent,
            },
          }
        )
        await dequeue(item.clientMessageId)
        queryClient.setQueryData<ConversationCache>(
          chatKeys.messages(item.conversationId),
          (current) => (current ? confirmSent(current, message) : current)
        )
      } catch (error) {
        if (!(error instanceof ApiError)) {
          return
        }
        await dequeue(item.clientMessageId)
        queryClient.setQueryData<ConversationCache>(
          chatKeys.messages(item.conversationId),
          (current) =>
            current ? markPendingFailed(current, item.clientMessageId) : current
        )
      }
    }
    if (queued.length > 0) {
      void refreshQueries(queryClient, chatKeys.conversations)
    }
  } finally {
    flushing = false
  }
}
