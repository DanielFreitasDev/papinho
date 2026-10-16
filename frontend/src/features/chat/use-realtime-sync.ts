import { useEffect } from "react"
import { useNavigate } from "@tanstack/react-router"
import { useQueryClient } from "@tanstack/react-query"

import { authQueries } from "@/features/auth"
import { refreshQueries } from "@/lib/query-refresh"
import { realtime } from "@/lib/realtime"
import { chatKeys, hideLocally, markDelivered, syncConversation } from "./api"
import { clearActivity, setActivity } from "./activity"
import { forgetMessages, invalidateFolders } from "./organization"
import type { Presence } from "./types"

/**
 * Liga o WebSocket enquanto há sessão e aplica os avisos ao cache: mensagens novas são buscadas por afterSeq,
 * confirmações e conversas são recarregadas, presença e "digitando" ficam na memória. Na reconexão sincroniza
 * tudo o que está aberto (RT-01). As recargas por aviso não cancelam a busca em andamento (refreshQueries).
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
          void refreshQueries(queryClient, chatKeys.conversations)
          void refreshQueries(
            queryClient,
            chatKeys.pinned(event.conversationId)
          )
          break
        case "receipts":
          void refreshQueries(queryClient, chatKeys.conversations)
          break
        case "conversation":
          void refreshQueries(queryClient, chatKeys.conversations)
          // Aceitar uma solicitação libera a presença dos dois lados (CT-02).
          void refreshQueries(queryClient, ["presence"])
          break
        case "hidden":
          hideLocally(queryClient, event.conversationId, event.messageIds)
          break
        case "cleared":
          forgetMessages(queryClient, event.conversationId)
          void refreshQueries(queryClient, chatKeys.conversations)
          break
        case "folders":
          void invalidateFolders(queryClient)
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
      const detail = queryClient.getQueryData<{
        peer: { userId: string } | null
      }>(chatKeys.detail(conversationId))
      // Só na conversa individual; em grupo (sem peer) cada membro tem o próprio aviso.
      if (detail?.peer) {
        clearActivity(conversationId, detail.peer.userId)
      }
    }
    const offState = realtime.onState((state) => {
      // Também na primeira conexão: o que mudou entre buscar a tela e assinar a fila só chega assim (RT-01).
      if (state === "connected") {
        void refreshQueries(queryClient, chatKeys.conversations)
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
