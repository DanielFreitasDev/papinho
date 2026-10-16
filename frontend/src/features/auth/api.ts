import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { setUnlockToken } from "@/lib/unlock-token"
import { ApiError, apiFetch } from "@/lib/api-client"
import { lastUserId, readSnapshot } from "@/lib/offline-db"
import { runLogoutCleanups } from "@/lib/logout-cleanup"
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
      queryFn: async ({ signal }) => {
        try {
          return (
            await apiFetch<{ me: Me | null }>("/api/v1/auth/session", {
              signal,
            })
          ).me
        } catch (error) {
          // Sem rede (não uma resposta do servidor): abre com a conta guardada para as conversas offline (NT-05).
          const userId = lastUserId()
          if (error instanceof ApiError || !userId) {
            throw error
          }
          return (await readSnapshot<Me>("me")) ?? null
        }
      },
      networkMode: "always",
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

/** Pede o link de redefinição por e-mail (ID-05); a resposta não diz se a conta existe. */
export function useRequestPasswordReset() {
  return useMutation({
    mutationFn: (username: string) =>
      apiFetch<void>("/api/v1/auth/password-reset", {
        method: "POST",
        json: { username },
      }),
  })
}

export function useConfirmPasswordReset() {
  return useMutation({
    mutationFn: (input: { token: string; newPassword: string }) =>
      apiFetch<void>("/api/v1/auth/password-reset/confirm", {
        method: "POST",
        json: input,
      }),
  })
}

export function verifyEmail(token: string): Promise<void> {
  return apiFetch<void>("/api/v1/auth/email/verify", {
    method: "POST",
    json: { token },
  })
}

/** Token dos links enviados por e-mail: vem no fragmento (`#token=`), que nunca vai ao servidor. */
export function tokenFromHash(hash: string): string | null {
  const token = new URLSearchParams(hash.replace(/^#/, "")).get("token")
  return token && /^[A-Za-z0-9_-]{16,128}$/.test(token) ? token : null
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      await runLogoutCleanups()
      return apiFetch<void>("/api/v1/auth/logout", { method: "POST" })
    },
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
