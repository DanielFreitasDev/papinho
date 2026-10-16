import {
  queryOptions,
  useMutation,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"
import { refreshQueries } from "@/lib/query-refresh"
import { chatKeys } from "./api"
import type { Conversation, GroupMember } from "./types"

export const groupKeys = {
  all: ["groups"] as const,
  members: (id: string) => ["groups", id, "members"] as const,
  invite: (id: string) => ["groups", id, "invite"] as const,
  requests: (id: string) => ["groups", id, "requests"] as const,
  preview: (code: string) => ["group-invite", code] as const,
  recipients: (id: string) => ["groups", id, "recipients"] as const,
}

export type BroadcastInput = {
  name: string
  recipientIds: string[]
  expiresAt: string | null
  messageTtlSeconds: number | null
}

/** Cria ou edita uma lista de transmissão (GR-03, GR-04). */
export function useSaveBroadcast(id?: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: BroadcastInput) =>
      apiFetch<Conversation>(
        id ? `/api/v1/broadcasts/${id}` : "/api/v1/broadcasts",
        {
          method: id ? "PUT" : "POST",
          json: input,
        }
      ),
    onSuccess: (conversation) => {
      queryClient.setQueryData(chatKeys.detail(conversation.id), conversation)
      refreshGroup(queryClient, conversation.id)
    },
  })
}

export type GroupInvite = { code: string; url: string; qrSvg: string }

export type InvitePreview = {
  groupId: string
  name: string
  description: string
  memberCount: number
  joinApproval: boolean
  member: boolean
  pending: boolean
}

export const groupQueries = {
  members: (id: string) =>
    queryOptions({
      queryKey: groupKeys.members(id),
      queryFn: ({ signal }) =>
        apiFetch<GroupMember[]>(`/api/v1/groups/${id}/members`, { signal }),
    }),
  invite: (id: string) =>
    queryOptions({
      queryKey: groupKeys.invite(id),
      queryFn: ({ signal }) =>
        apiFetch<GroupInvite>(`/api/v1/groups/${id}/invite`, { signal }),
    }),
  requests: (id: string) =>
    queryOptions({
      queryKey: groupKeys.requests(id),
      queryFn: ({ signal }) =>
        apiFetch<GroupMember[]>(`/api/v1/groups/${id}/requests`, { signal }),
    }),
  recipients: (id: string) =>
    queryOptions({
      queryKey: groupKeys.recipients(id),
      queryFn: ({ signal }) =>
        apiFetch<GroupMember[]>(`/api/v1/broadcasts/${id}/recipients`, {
          signal,
        }),
    }),
  preview: (code: string) =>
    queryOptions({
      queryKey: groupKeys.preview(code),
      queryFn: ({ signal }) =>
        apiFetch<InvitePreview>(`/api/v1/groups/invites/${code}`, { signal }),
      retry: false,
    }),
}

/** Depois de qualquer mudança no grupo: conversa, lista e membros. */
export function refreshGroup(queryClient: QueryClient, id: string) {
  void refreshQueries(queryClient, chatKeys.conversations)
  void queryClient.invalidateQueries({ queryKey: ["groups", id] })
}

export function useCreateGroup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      name: string
      description?: string
      memberIds: string[]
    }) =>
      apiFetch<Conversation>("/api/v1/groups", { method: "POST", json: input }),
    onSuccess: (conversation) => {
      queryClient.setQueryData(chatKeys.detail(conversation.id), conversation)
      void refreshQueries(queryClient, chatKeys.conversations)
    },
  })
}

/** Mensagens salvas (MS-12): abre ou cria a conversa consigo mesmo. */
export function useOpenSaved() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiFetch<Conversation>("/api/v1/conversations/saved", {
        method: "POST",
      }),
    onSuccess: (conversation) =>
      queryClient.setQueryData(chatKeys.detail(conversation.id), conversation),
  })
}

type GroupAction =
  | { action: "info"; name?: string; description?: string }
  | { action: "photo"; mediaId: string | null }
  | {
      action: "settings"
      onlyAdminsSend: boolean
      onlyAdminsEditInfo: boolean
      onlyAdminsAddMembers: boolean
      joinApproval: boolean
    }
  | { action: "add"; userIds: string[] }
  | { action: "remove"; userId: string }
  | { action: "admin"; userId: string; admin: boolean }
  | { action: "leave" }
  | { action: "resetInvite" }
  | { action: "revokeInvite" }
  | { action: "decide"; userId: string; approve: boolean }

/** Ações sobre um grupo (GR-01, GR-02); o servidor confere as permissões. */
export function useGroupAction(id: string) {
  const queryClient = useQueryClient()
  const base = `/api/v1/groups/${id}`
  return useMutation({
    mutationFn: (input: GroupAction): Promise<unknown> => {
      switch (input.action) {
        case "info":
          return apiFetch(base, {
            method: "PATCH",
            json: { name: input.name, description: input.description },
          })
        case "photo":
          return apiFetch(`${base}/photo`, {
            method: "PUT",
            json: { mediaId: input.mediaId },
          })
        case "settings": {
          const { action: _ignored, ...settings } = input
          void _ignored
          return apiFetch(`${base}/settings`, { method: "PUT", json: settings })
        }
        case "add":
          return apiFetch(`${base}/members`, {
            method: "POST",
            json: { userIds: input.userIds },
          })
        case "remove":
          return apiFetch(`${base}/members/${input.userId}`, {
            method: "DELETE",
          })
        case "admin":
          return apiFetch(`${base}/admins/${input.userId}`, {
            method: input.admin ? "PUT" : "DELETE",
          })
        case "leave":
          return apiFetch(`${base}/leave`, { method: "POST" })
        case "resetInvite":
          return apiFetch(`${base}/invite/reset`, { method: "POST" })
        case "revokeInvite":
          return apiFetch(`${base}/invite`, { method: "DELETE" })
        case "decide":
          return apiFetch(
            `${base}/requests/${input.userId}/${input.approve ? "approve" : "decline"}`,
            { method: "POST" }
          )
      }
    },
    onSuccess: () => refreshGroup(queryClient, id),
  })
}

export function useJoinGroup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (code: string) =>
      apiFetch<{ groupId: string; pending: boolean }>(
        `/api/v1/groups/invites/${code}/join`,
        { method: "POST" }
      ),
    onSuccess: () => refreshQueries(queryClient, chatKeys.conversations),
  })
}
