import {
  infiniteQueryOptions,
  queryOptions,
  useMutation,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query"

import { apiFetch, ApiError } from "@/lib/api-client"
import {
  addPending,
  applyChanges,
  confirmSent,
  fromHistory,
  markPendingFailed,
  prependOlder,
  removeMessages,
  type ConversationCache,
} from "./cache"
import type {
  Conversation,
  LocalAttachment,
  Message,
  MessageInfo,
  MessagePage,
  Presence,
  ReplyPreview,
  ScheduledMessage,
} from "./types"

export const chatKeys = {
  conversations: ["conversations"] as const,
  list: (requests: boolean) => ["conversations", "list", requests] as const,
  /** Pasta das trancadas (PV-03): só com o token de desbloqueio. */
  locked: ["conversations", "locked"] as const,
  detail: (id: string) => ["conversations", "detail", id] as const,
  messages: (id: string) => ["messages", id] as const,
  pinned: (id: string) => ["messages", id, "pinned"] as const,
  starred: ["starred"] as const,
  info: (messageId: string) => ["message-info", messageId] as const,
  presence: (userId: string) => ["presence", userId] as const,
  preferences: ["messaging-preferences"] as const,
  scheduled: (id: string) => ["scheduled", id] as const,
  gallery: (id: string, tab: GalleryTab) =>
    ["messages", id, "gallery", tab] as const,
}

export type GalleryTab = "MEDIA" | "LINKS" | "DOCUMENTS" | "AUDIO"

const PAGE = 50

export const chatQueries = {
  list: (requests = false) =>
    queryOptions({
      queryKey: chatKeys.list(requests),
      queryFn: ({ signal }) =>
        apiFetch<Conversation[]>(
          `/api/v1/conversations${requests ? "?requests=true" : ""}`,
          { signal }
        ),
    }),
  locked: () =>
    queryOptions({
      queryKey: chatKeys.locked,
      queryFn: ({ signal }) =>
        apiFetch<Conversation[]>("/api/v1/conversations?locked=true", {
          signal,
        }),
    }),
  detail: (id: string) =>
    queryOptions({
      queryKey: chatKeys.detail(id),
      queryFn: ({ signal }) =>
        apiFetch<Conversation>(`/api/v1/conversations/${id}`, { signal }),
      retry: (count, error) =>
        !(
          error instanceof ApiError &&
          (error.status === 404 || error.status === 403)
        ) && count < 2,
    }),
  /** Mensagens carregadas da conversa; o WebSocket e a reconexão mantêm o cache em dia. */
  messages: (id: string) =>
    queryOptions({
      queryKey: chatKeys.messages(id),
      queryFn: async ({ signal }) =>
        fromHistory(
          await apiFetch<MessagePage>(
            `/api/v1/conversations/${id}/messages?limit=${PAGE}`,
            { signal }
          )
        ),
      staleTime: Infinity,
      gcTime: 5 * 60_000,
      retry: (count, error) =>
        !(
          error instanceof ApiError &&
          (error.status === 404 || error.status === 403)
        ) && count < 2,
    }),
  pinned: (id: string) =>
    queryOptions({
      queryKey: chatKeys.pinned(id),
      queryFn: ({ signal }) =>
        apiFetch<Message[]>(`/api/v1/conversations/${id}/pinned`, { signal }),
    }),
  starred: () =>
    queryOptions({
      queryKey: chatKeys.starred,
      queryFn: ({ signal }) =>
        apiFetch<Message[]>("/api/v1/messages/starred", { signal }),
    }),
  info: (messageId: string) =>
    queryOptions({
      queryKey: chatKeys.info(messageId),
      queryFn: ({ signal }) =>
        apiFetch<MessageInfo>(`/api/v1/messages/${messageId}/info`, {
          signal,
        }),
    }),
  /** Galeria da conversa (MD-03), paginada para trás por seq. */
  gallery: (id: string, tab: GalleryTab) =>
    infiniteQueryOptions({
      queryKey: chatKeys.gallery(id, tab),
      queryFn: ({ signal, pageParam }) =>
        apiFetch<MessagePage>(
          `/api/v1/conversations/${id}/gallery?tab=${tab}&limit=30${pageParam ? `&beforeSeq=${pageParam}` : ""}`,
          { signal }
        ),
      initialPageParam: 0,
      getNextPageParam: (page) =>
        page.hasMore ? (page.messages.at(-1)?.seq ?? undefined) : undefined,
    }),
  scheduled: (id: string) =>
    queryOptions({
      queryKey: chatKeys.scheduled(id),
      queryFn: ({ signal }) =>
        apiFetch<ScheduledMessage[]>(`/api/v1/conversations/${id}/scheduled`, {
          signal,
        }),
    }),
  preferences: () =>
    queryOptions({
      queryKey: chatKeys.preferences,
      queryFn: ({ signal }) =>
        apiFetch<{ defaultDisappearingSeconds: number | null }>(
          "/api/v1/messaging/preferences",
          { signal }
        ),
    }),
  presence: (userId: string) =>
    queryOptions({
      queryKey: chatKeys.presence(userId),
      queryFn: ({ signal }) =>
        apiFetch<Presence>(`/api/v1/presence/${userId}`, { signal }),
      retry: false,
      staleTime: 60_000,
    }),
}

const syncing = new Map<string, Promise<void>>()
const syncAgain = new Set<string>()

/**
 * Traz o que mudou desde o cursor da conversa (RT-01). Chamadas simultâneas para a mesma conversa são
 * agrupadas; com {@code hasMore} continua até alcançar o fim. Um aviso que chega durante uma sincronização
 * agenda mais uma rodada ao final: a busca em andamento pode ter começado antes da mudança avisada.
 */
export function syncConversation(queryClient: QueryClient, id: string) {
  const running = syncing.get(id)
  if (running) {
    syncAgain.add(id)
    return running
  }
  const task = (async () => {
    for (let round = 0; round < 20; round++) {
      syncAgain.delete(id)
      const cache = queryClient.getQueryData<ConversationCache>(
        chatKeys.messages(id)
      )
      if (!cache) {
        return
      }
      const page = await apiFetch<MessagePage>(
        `/api/v1/conversations/${id}/messages?afterSeq=${cache.cursor}&limit=500`
      )
      queryClient.setQueryData<ConversationCache>(
        chatKeys.messages(id),
        (current) => (current ? applyChanges(current, page) : current)
      )
      if (!page.hasMore && !syncAgain.has(id)) {
        return
      }
    }
  })().finally(() => syncing.delete(id))
  syncing.set(id, task)
  return task
}

export async function loadOlder(queryClient: QueryClient, id: string) {
  const cache = queryClient.getQueryData<ConversationCache>(
    chatKeys.messages(id)
  )
  if (!cache || !cache.hasOlder) {
    return
  }
  const before = cache.messages[0]?.seq
  const page = await apiFetch<MessagePage>(
    `/api/v1/conversations/${id}/messages?limit=${PAGE}${before ? `&beforeSeq=${before}` : ""}`
  )
  queryClient.setQueryData<ConversationCache>(
    chatKeys.messages(id),
    (current) => (current ? prependOlder(current, page) : current)
  )
}

const deliveredSent = new Map<string, number>()

/** Avisa que este aparelho recebeu até {@code seq} (MS-02), sem repetir o mesmo aviso. */
export function markDelivered(id: string, seq: number) {
  if ((deliveredSent.get(id) ?? 0) >= seq) {
    return
  }
  deliveredSent.set(id, seq)
  void apiFetch<void>(`/api/v1/conversations/${id}/delivered`, {
    method: "PUT",
    json: { seq },
  }).catch(() => deliveredSent.delete(id))
}

export function markRead(queryClient: QueryClient, id: string, seq: number) {
  deliveredSent.set(id, Math.max(deliveredSent.get(id) ?? 0, seq))
  return apiFetch<void>(`/api/v1/conversations/${id}/read`, {
    method: "PUT",
    json: { seq },
  }).then(() =>
    queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
  )
}

function updateMessage(queryClient: QueryClient, message: Message) {
  queryClient.setQueryData<ConversationCache>(
    chatKeys.messages(message.conversationId),
    (current) => (current ? confirmSent(current, message) : current)
  )
}

/** Envio com clientMessageId: reenviar depois de uma falha não duplica (RT-02). */
export function useSendMessage(conversationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      clientMessageId: string
      body: string
      replyTo: ReplyPreview | null
      attachmentId?: string | null
      attachment?: LocalAttachment | null
      /** Sem notificação para quem recebe (MS-11). */
      silent?: boolean
      /** Figurinha (MS-10). */
      stickerId?: string | null
    }) =>
      apiFetch<Message>(`/api/v1/conversations/${conversationId}/messages`, {
        method: "POST",
        json: {
          clientMessageId: input.clientMessageId,
          body: input.body,
          replyToId: input.replyTo?.id ?? null,
          attachmentId: input.attachmentId ?? null,
          viewOnce: input.attachment?.viewOnce ?? false,
          silent: input.silent ?? false,
          stickerId: input.stickerId ?? null,
        },
      }),
    onMutate: (input) => {
      queryClient.setQueryData<ConversationCache>(
        chatKeys.messages(conversationId),
        (current) =>
          current
            ? addPending(current, {
                clientMessageId: input.clientMessageId,
                conversationId,
                body: input.body,
                replyTo: input.replyTo,
                createdAt: new Date().toISOString(),
                status: "sending",
                attachment: input.attachment ?? null,
                attachmentId: input.attachmentId ?? null,
              })
            : current
      )
    },
    onSuccess: (message) => {
      updateMessage(queryClient, message)
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
    },
    onError: (_error, input) => {
      queryClient.setQueryData<ConversationCache>(
        chatKeys.messages(conversationId),
        (current) =>
          current ? markPendingFailed(current, input.clientMessageId) : current
      )
    },
  })
}

export function useOpenDirect() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (username: string) =>
      apiFetch<Conversation>("/api/v1/conversations/direct", {
        method: "POST",
        json: { username },
      }),
    onSuccess: (conversation) => {
      queryClient.setQueryData(chatKeys.detail(conversation.id), conversation)
    },
  })
}

export function useMessageAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      messageId: string
      action: "react" | "unreact" | "pin" | "unpin" | "star" | "unstar"
      emoji?: string
    }) => {
      const base = `/api/v1/messages/${input.messageId}`
      switch (input.action) {
        case "react":
          return apiFetch<Message>(`${base}/reaction`, {
            method: "PUT",
            json: { emoji: input.emoji },
          })
        case "unreact":
          return apiFetch<Message>(`${base}/reaction`, { method: "DELETE" })
        case "pin":
          return apiFetch<Message>(`${base}/pin`, { method: "PUT" })
        case "unpin":
          return apiFetch<Message>(`${base}/pin`, { method: "DELETE" })
        case "star":
          return apiFetch<Message>(`${base}/star`, { method: "PUT" })
        case "unstar":
          return apiFetch<Message>(`${base}/star`, { method: "DELETE" })
      }
    },
    onSuccess: (message) => {
      updateMessage(queryClient, message)
      void queryClient.invalidateQueries({
        queryKey: chatKeys.pinned(message.conversationId),
      })
      void queryClient.invalidateQueries({ queryKey: chatKeys.starred })
    },
  })
}

/** Edita o texto de uma mensagem própria (MS-05). */
export function useEditMessage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { messageId: string; body: string }) =>
      apiFetch<Message>(`/api/v1/messages/${input.messageId}`, {
        method: "PATCH",
        json: { body: input.body },
      }),
    onSuccess: (message) => {
      updateMessage(queryClient, message)
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
    },
  })
}

/** Apaga para todos (MS-06). */
export function useDeleteForEveryone() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (messageId: string) =>
      apiFetch<Message>(`/api/v1/messages/${messageId}`, { method: "DELETE" }),
    onSuccess: (message) => {
      updateMessage(queryClient, message)
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
      void queryClient.invalidateQueries({
        queryKey: chatKeys.pinned(message.conversationId),
      })
      void queryClient.invalidateQueries({ queryKey: chatKeys.starred })
    },
  })
}

/** Tira as mensagens do cache local de uma conversa (apagar para mim, MS-06). */
export function hideLocally(
  queryClient: QueryClient,
  conversationId: string,
  messageIds: string[]
) {
  queryClient.setQueryData<ConversationCache>(
    chatKeys.messages(conversationId),
    (current) => (current ? removeMessages(current, messageIds) : current)
  )
  void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
  void queryClient.invalidateQueries({
    queryKey: chatKeys.pinned(conversationId),
  })
  void queryClient.invalidateQueries({ queryKey: chatKeys.starred })
}

/** Apaga para mim (MS-06): some em todos os meus dispositivos. */
export function useHideMessages(conversationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (messageIds: string[]) =>
      apiFetch<void>("/api/v1/messages/hide", {
        method: "POST",
        json: { messageIds },
      }),
    onSuccess: (_result, messageIds) =>
      hideLocally(queryClient, conversationId, messageIds),
  })
}

/** Guarda o rascunho da conversa no servidor (MS-11); texto vazio apaga. */
export function saveDraft(conversationId: string, body: string) {
  return apiFetch<void>(`/api/v1/conversations/${conversationId}/draft`, {
    method: "PUT",
    json: { body },
  })
}

/** Agenda ou cancela mensagens (MS-11). */
export function useScheduleAction(conversationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (
      input:
        | { action: "schedule"; body: string; sendAt: string; silent: boolean }
        | { action: "cancel"; id: string }
    ): Promise<unknown> =>
      input.action === "schedule"
        ? apiFetch<ScheduledMessage>(
            `/api/v1/conversations/${conversationId}/scheduled`,
            {
              method: "POST",
              json: {
                body: input.body,
                sendAt: input.sendAt,
                silent: input.silent,
              },
            }
          )
        : apiFetch<void>(`/api/v1/scheduled/${input.id}`, { method: "DELETE" }),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: chatKeys.scheduled(conversationId),
      }),
  })
}

/** Cria uma enquete (MS-09). */
export function useCreatePoll(conversationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      question: string
      options: string[]
      multiple: boolean
      anonymous: boolean
      closesAt: string | null
    }) =>
      apiFetch<Message>(`/api/v1/conversations/${conversationId}/polls`, {
        method: "POST",
        json: { clientMessageId: crypto.randomUUID(), ...input },
      }),
    onSuccess: (message) => {
      updateMessage(queryClient, message)
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
    },
  })
}

/** Vota numa enquete; lista vazia tira o voto (MS-09). */
export function useVote() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { messageId: string; optionIds: string[] }) =>
      apiFetch<Message>(`/api/v1/messages/${input.messageId}/vote`, {
        method: "PUT",
        json: { optionIds: input.optionIds },
      }),
    onSuccess: (message) => updateMessage(queryClient, message),
  })
}

/** Timer de mensagens temporárias da conversa (MS-07). */
/** Confirmação de leitura nesta conversa (PV-06): ligada, desligada ou o padrão ({@code null}). */
export function useReadReceipts(conversationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (enabled: boolean | null) =>
      apiFetch<Conversation>(
        `/api/v1/conversations/${conversationId}/read-receipts`,
        { method: "PUT", json: { enabled } }
      ),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: chatKeys.conversations }),
  })
}

export function useDisappearing(conversationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (seconds: number | null) =>
      apiFetch<void>(`/api/v1/conversations/${conversationId}/disappearing`, {
        method: "PUT",
        json: { seconds },
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: chatKeys.conversations }),
  })
}

/** Timer padrão para conversas novas (MS-07). */
export function useChangePreferences() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (defaultDisappearingSeconds: number | null) =>
      apiFetch<{ defaultDisappearingSeconds: number | null }>(
        "/api/v1/messaging/preferences",
        { method: "PUT", json: { defaultDisappearingSeconds } }
      ),
    onSuccess: (preferences) =>
      queryClient.setQueryData(chatKeys.preferences, preferences),
  })
}

export function useForward() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { messageIds: string[]; conversationIds: string[] }) =>
      apiFetch<Message[]>("/api/v1/messages/forward", {
        method: "POST",
        json: input,
      }),
    onSuccess: (messages) => {
      messages.forEach((message) => updateMessage(queryClient, message))
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
    },
  })
}

export function useConversationAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      conversationId: string
      action: "accept" | "decline" | "unread"
    }) => {
      const base = `/api/v1/conversations/${input.conversationId}`
      if (input.action === "unread") {
        return apiFetch<void>(`${base}/unread`, { method: "PUT" })
      }
      return apiFetch<void>(`${base}/${input.action}`, { method: "POST" })
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: chatKeys.conversations }),
  })
}
