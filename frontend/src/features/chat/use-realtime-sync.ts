import { useEffect } from "react"
import { useNavigate } from "@tanstack/react-router"
import { useQueryClient } from "@tanstack/react-query"

import { authQueries } from "@/features/auth"
import { realtime } from "@/lib/realtime"
import { chatKeys, hideLocally, markDelivered, syncConversation } from "./api"
import { clearActivity, setActivity } from "./activity"
import type { Presence } from "./types"

/**
 * Liga o WebSocket enquanto há sessão e aplica os avisos ao cache: mensagens novas são buscadas por afterSeq,
 * confirmações e conversas são recarregadas, presença e "digitando" ficam na memória. Na reconexão sincroniza
 * tudo o que está aberto (RT-01).
 */
export function useRealtimeSync(meId: string) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  useEffect(() => {
    const syncOpen = () => {
      queryClient
        .getQueriesData({ queryKey: ["messages"] })
        .forEach(([key]) => {
          if (key.length === 2) {
            void syncConversation(queryClient, String(key[1]))
          }
        })
    }
    const offEvents = realtime.subscribe((event) => {
      switch (event.type) {
        case "messages":
          void syncConversation(queryClient, event.conversationId)
          markDelivered(event.conversationId, event.lastSeq)
          clearActivityFromOthers(event.conversationId)
          void queryClient.invalidateQueries({
            queryKey: chatKeys.conversations,
          })
          void queryClient.invalidateQueries({
            queryKey: chatKeys.pinned(event.conversationId),
          })
          break
        case "receipts":
          void queryClient.invalidateQueries({
            queryKey: chatKeys.conversations,
          })
          break
        case "conversation":
          void queryClient.invalidateQueries({
            queryKey: chatKeys.conversations,
          })
          // Aceitar uma solicitação libera a presença dos dois lados (CT-02).
          void queryClient.invalidateQueries({ queryKey: ["presence"] })
          break
        case "hidden":
          hideLocally(queryClient, event.conversationId, event.messageIds)
          break
        case "activity":
          if (event.userId !== meId) {
            setActivity(event.conversationId, event.userId, event.activity)
          }
          break
        case "presence":
          queryClient.setQueryData<Presence>(chatKeys.presence(event.userId), {
            userId: event.userId,
            online: event.online,
            lastSeenAt: event.lastSeenAt,
          })
          break
      }
    })
    // Uma mensagem nova encerra o "digitando" de quem a mandou.
    const clearActivityFromOthers = (conversationId: string) => {
      const peers = queryClient.getQueryData<{ peer: { userId: string } }>(
        chatKeys.detail(conversationId)
      )
      if (peers) {
        clearActivity(conversationId, peers.peer.userId)
      }
    }
    const offState = realtime.onState((state, reconnected) => {
      if (state === "connected" && reconnected) {
        void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
        syncOpen()
      }
      if (state === "ended") {
        queryClient.setQueryData(authQueries.me().queryKey, null)
        void navigate({ to: "/login" })
      }
    })
    realtime.start()
    return () => {
      offEvents()
      offState()
      realtime.stop()
    }
  }, [meId, navigate, queryClient])
}
