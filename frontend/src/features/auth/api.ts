import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { setUnlockToken } from "@/lib/unlock-token"
import { apiFetch } from "@/lib/api-client"
import type { Instance, Me, RegisterResponse } from "./types"

export const authKeys = {
  me: ["auth", "me"] as const,
  instance: ["instance"] as const,
}

export const authQueries = {
  /** Usuário da sessão atual; `null` quando não há sessão (rota pública, sem 401 no console). */
  me: () =>
    queryOptions({
      queryKey: authKeys.me,
      queryFn: async ({ signal }) =>
        (await apiFetch<{ me: Me | null }>("/api/v1/auth/session", { signal }))
          .me,
      staleTime: 60_000,
    }),
  instance: () =>
    queryOptions({
      queryKey: authKeys.instance,
      queryFn: ({ signal }) =>
        apiFetch<Instance>("/api/v1/instance", { signal }),
      staleTime: 5 * 60_000,
    }),
}

export type LoginInput = {
  username: string
  password: string
  totpCode?: string
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: LoginInput) =>
      apiFetch<Me>("/api/v1/auth/login", { method: "POST", json: input }),
    onSuccess: (me) => queryClient.setQueryData(authKeys.me, me),
  })
}

export type RegisterInput = {
  username: string
  displayName: string
  password: string
  inviteToken?: string
}

export function useRegister() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: RegisterInput) =>
      apiFetch<RegisterResponse>("/api/v1/auth/register", {
        method: "POST",
        json: input,
      }),
    // A instância (setupRequired) só é recarregada quando o usuário sai da tela de cadastro: recarregar
    // aqui trocaria a tela e desmontaria o diálogo dos códigos de recuperação antes de serem vistos.
    onSuccess: (response) => queryClient.setQueryData(authKeys.me, response.me),
  })
}

export type RecoverInput = {
  username: string
  recoveryCode: string
  newPassword: string
}

export function useRecover() {
  return useMutation({
    mutationFn: (input: RecoverInput) =>
      apiFetch<void>("/api/v1/auth/recover", { method: "POST", json: input }),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/auth/logout", { method: "POST" }),
    onSettled: () => {
      // Nada da conta anterior fica em memória (NT-05): limpa todo o cache do TanStack Query e o token das
      // conversas trancadas (PV-03).
      setUnlockToken(null)
      queryClient.clear()
      queryClient.setQueryData(authKeys.me, null)
    },
  })
}

export async function checkInvite(token: string): Promise<boolean> {
  const result = await apiFetch<{ usable: boolean }>(
    `/api/v1/invites/status?token=${encodeURIComponent(token)}`
  )
  return result.usable
}

export async function checkUsername(username: string): Promise<boolean> {
  const result = await apiFetch<{ available: boolean }>(
    `/api/v1/usernames/availability?username=${encodeURIComponent(username)}`
  )
  return result.available
}
