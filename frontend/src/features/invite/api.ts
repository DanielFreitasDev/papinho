import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"

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
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: inviteKeys.list }),
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
