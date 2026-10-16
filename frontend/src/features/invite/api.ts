import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"
import { refreshQueries } from "@/lib/query-refresh"

export type Invite = {
  id: string
  createdAt: string
  expiresAt: string
  maxUses: number
  uses: number
  revoked: boolean
  usable: boolean
}

export type CreatedInvite = { invite: Invite; url: string; qrSvg: string }

export const inviteKeys = { list: ["invites"] as const }

export const inviteQueries = {
  list: () =>
    queryOptions({
      queryKey: inviteKeys.list,
      queryFn: ({ signal }) =>
        apiFetch<Invite[]>("/api/v1/invites", { signal }),
    }),
}

export function useCreateInvite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { validHours: number; maxUses: number }) =>
      apiFetch<CreatedInvite>("/api/v1/invites", {
        method: "POST",
        json: input,
      }),
    // O diálogo cria convite antes de a lista carregar: invalidar reaproveitaria a primeira busca, sem o convite
    // novo. Sem await, o link criado aparece na hora.
    onSuccess: () => {
      void refreshQueries(queryClient, inviteKeys.list)
    },
  })
}

export function useRevokeInvite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/invites/${id}`, { method: "DELETE" }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: inviteKeys.list }),
  })
}
