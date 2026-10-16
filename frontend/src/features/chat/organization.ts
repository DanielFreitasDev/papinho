import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"
import { refreshQueries } from "@/lib/query-refresh"
import { chatKeys } from "./api"
import type {
  Conversation,
  Folder,
  Message,
  MessagePage,
  MuteDuration,
} from "./types"

/** Organização da lista (GR-05) e busca (GR-06). */
export const organizationKeys = {
  folders: ["folders"] as const,
  search: (query: string) => ["search", query] as const,
  inConversation: (conversationId: string, params: string) =>
    ["search", conversationId, params] as const,
}

export const organizationQueries = {
  folders: () =>
    queryOptions({
      queryKey: organizationKeys.folders,
      queryFn: ({ signal }) =>
        apiFetch<Folder[]>("/api/v1/folders", { signal }),
    }),
  /** Busca global de mensagens; conversas e contatos são filtrados aqui mesmo. */
  search: (query: string) =>
    queryOptions({
      queryKey: organizationKeys.search(query),
      queryFn: ({ signal }) =>
        apiFetch<Message[]>(
          `/api/v1/search/messages?q=${encodeURIComponent(query)}`,
          { signal }
        ),
      enabled: query.trim().length >= 2,
      staleTime: 10_000,
    }),
  inConversation: (conversationId: string, filters: SearchFilters) => {
    const params = searchParams(filters)
    return queryOptions({
      queryKey: organizationKeys.inConversation(conversationId, params),
      queryFn: ({ signal }) =>
        apiFetch<MessagePage>(
          `/api/v1/conversations/${conversationId}/search?${params}`,
          { signal }
        ),
      enabled: hasFilters(filters),
    })
  },
}

/** Filtros da busca dentro da conversa (GR-06). */
export type SearchFilters = {
  query: string
  senderId: string | null
  /** Dia (aaaa-mm-dd) no fuso do navegador. */
  date: string | null
  type: "MEDIA" | "LINKS" | "DOCUMENTS" | "AUDIO" | null
}

export function hasFilters(filters: SearchFilters) {
  return (
    filters.query.trim().length >= 2 ||
    filters.senderId !== null ||
    filters.date !== null ||
    filters.type !== null
  )
}

function searchParams(filters: SearchFilters) {
  const params = new URLSearchParams({ limit: "50" })
  if (filters.query.trim().length >= 2) {
    params.set("q", filters.query.trim())
  }
  if (filters.senderId) {
    params.set("senderId", filters.senderId)
  }
  if (filters.date) {
    const start = new Date(`${filters.date}T00:00:00`)
    const end = new Date(start)
    end.setDate(end.getDate() + 1)
    params.set("from", start.toISOString())
    params.set("to", end.toISOString())
  }
  if (filters.type) {
    params.set("type", filters.type)
  }
  return params.toString()
}

export type ListAction =
  | { action: "archive" | "pin" | "favorite"; on: boolean }
  | { action: "mute"; duration: MuteDuration | null; mentions: boolean }
  | { action: "clear"; keepStarred: boolean }
  | { action: "remove" }

/** Arquivar, fixar, favoritar, silenciar, limpar e apagar (GR-05). */
export function useListAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      conversationId,
      ...input
    }: ListAction & { conversationId: string }) => {
      const base = `/api/v1/conversations/${conversationId}`
      switch (input.action) {
        case "archive":
        case "pin":
        case "favorite":
          return apiFetch<void>(`${base}/${input.action}`, {
            method: input.on ? "PUT" : "DELETE",
          })
        case "mute":
          return apiFetch<void>(`${base}/mute`, {
            method: "PUT",
            json: { duration: input.duration, mentions: input.mentions },
          })
        case "clear":
          return apiFetch<void>(`${base}/clear`, {
            method: "POST",
            json: { keepStarred: input.keepStarred },
          })
        case "remove":
          return apiFetch<void>(base, { method: "DELETE" })
      }
    },
    onSuccess: (_, input) => {
      if (input.action === "clear" || input.action === "remove") {
        forgetMessages(queryClient, input.conversationId)
      }
      return refreshQueries(queryClient, chatKeys.conversations)
    },
  })
}

/** Limpar ou apagar a conversa esvazia a tela (também quando vem de outro dispositivo). */
export function forgetMessages(
  queryClient: ReturnType<typeof useQueryClient>,
  conversationId: string
) {
  // Reinicia (e busca de novo, se a conversa está aberta) em vez de só remover do cache.
  void queryClient.resetQueries({
    queryKey: chatKeys.messages(conversationId),
    exact: true,
  })
  void queryClient.invalidateQueries({ queryKey: ["starred"] })
}

export function useSaveFolder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      id?: string
      name: string
      conversationIds: string[]
    }) =>
      apiFetch<Folder>(
        input.id ? `/api/v1/folders/${input.id}` : "/api/v1/folders",
        {
          method: input.id ? "PUT" : "POST",
          json: { name: input.name, conversationIds: input.conversationIds },
        }
      ),
    onSuccess: () => invalidateFolders(queryClient),
  })
}

export function useDeleteFolder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/folders/${id}`, { method: "DELETE" }),
    onSuccess: () => invalidateFolders(queryClient),
  })
}

/** Põe ou tira a conversa de uma pasta pelo menu da conversa. */
export function useToggleFolder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      folderId: string
      conversationId: string
      on: boolean
    }) =>
      apiFetch<Folder>(
        `/api/v1/folders/${input.folderId}/conversations/${input.conversationId}`,
        { method: input.on ? "PUT" : "DELETE" }
      ),
    onSuccess: () => invalidateFolders(queryClient),
  })
}

export function invalidateFolders(
  queryClient: ReturnType<typeof useQueryClient>
) {
  void queryClient.invalidateQueries({ queryKey: organizationKeys.folders })
  return refreshQueries(queryClient, chatKeys.conversations)
}

/** Silenciada agora (o "até" ainda não passou). */
export function isMuted(conversation: Conversation, now = Date.now()) {
  const until = conversation.organization.mutedUntil
  return until !== null && Date.parse(until) > now
}

/** "Sempre" é guardado como o ano 9999. */
export function mutedForever(conversation: Conversation) {
  return (conversation.organization.mutedUntil ?? "").startsWith("9999")
}

/** Filtros da lista de conversas (GR-06). */
export type ListFilter =
  | { kind: "all" | "unread" | "favorites" | "groups" | "archived" }
  | { kind: "folder"; folderId: string }

export function applyListFilter(
  conversations: Conversation[],
  filter: ListFilter
) {
  return conversations.filter((conversation) => {
    const { archived, favorite, folderIds } = conversation.organization
    switch (filter.kind) {
      case "archived":
        return archived
      case "all":
        return !archived
      case "unread":
        return (
          !archived &&
          (conversation.unreadCount > 0 || conversation.markedUnread)
        )
      case "favorites":
        return favorite
      case "groups":
        return !archived && conversation.kind === "GROUP"
      case "folder":
        return folderIds.includes(filter.folderId)
    }
  })
}

/** Texto sem acentos e em minúsculas, para filtrar nomes no cliente. */
export function normalize(text: string) {
  return text.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase()
}
