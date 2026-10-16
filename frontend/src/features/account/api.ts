import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { authKeys, type Account, type Profile } from "@/features/auth"
import { apiFetch } from "@/lib/api-client"

export type SessionInfo = {
  id: string
  device: string
  approximateIp: string
  createdAt: string
  lastAccessedAt: string
  current: boolean
}

export type Passkey = {
  id: string
  label: string
  createdAt: string | null
  lastUsedAt: string | null
}

export type TotpEnrollment = {
  secret: string
  otpauthUri: string
  qrSvg: string
}

export const accountKeys = {
  sessions: ["account", "sessions"] as const,
  passkeys: ["account", "passkeys"] as const,
}

export const accountQueries = {
  sessions: () =>
    queryOptions({
      queryKey: accountKeys.sessions,
      queryFn: ({ signal }) =>
        apiFetch<SessionInfo[]>("/api/v1/sessions", { signal }),
    }),
  passkeys: () =>
    queryOptions({
      queryKey: accountKeys.passkeys,
      queryFn: ({ signal }) =>
        apiFetch<Passkey[]>("/api/v1/account/passkeys", { signal }),
    }),
}

function useRefreshMe() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: authKeys.me })
}

export function useUpdateProfile() {
  const refresh = useRefreshMe()
  return useMutation({
    mutationFn: (input: { displayName: string; about: string | null }) =>
      apiFetch<Profile>("/api/v1/profile", { method: "PUT", json: input }),
    onSuccess: refresh,
  })
}

export function useChangePhoto() {
  const refresh = useRefreshMe()
  return useMutation({
    mutationFn: (photo: Blob) => {
      const body = new FormData()
      body.append("photo", photo, "foto.jpg")
      return apiFetch<Profile>("/api/v1/profile/photo", { method: "PUT", body })
    },
    onSuccess: refresh,
  })
}

export function useRemovePhoto() {
  const refresh = useRefreshMe()
  return useMutation({
    mutationFn: () =>
      apiFetch<Profile>("/api/v1/profile/photo", { method: "DELETE" }),
    onSuccess: refresh,
  })
}

export function useChangeUsername() {
  const refresh = useRefreshMe()
  return useMutation({
    mutationFn: (username: string) =>
      apiFetch<Account>("/api/v1/account/username", {
        method: "PUT",
        json: { username },
      }),
    onSuccess: refresh,
  })
}

export function useChangePassword() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { currentPassword: string; newPassword: string }) =>
      apiFetch<void>("/api/v1/account/password", {
        method: "PUT",
        json: input,
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: accountKeys.sessions }),
  })
}

export function useStartTotp() {
  return useMutation({
    mutationFn: () =>
      apiFetch<TotpEnrollment>("/api/v1/account/totp", { method: "POST" }),
  })
}

export function useConfirmTotp() {
  const refresh = useRefreshMe()
  return useMutation({
    mutationFn: (code: string) =>
      apiFetch<void>("/api/v1/account/totp/confirm", {
        method: "POST",
        json: { code },
      }),
    onSuccess: refresh,
  })
}

export function useDisableTotp() {
  const refresh = useRefreshMe()
  return useMutation({
    mutationFn: (password: string) =>
      apiFetch<void>("/api/v1/account/totp", {
        method: "DELETE",
        json: { password },
      }),
    onSuccess: refresh,
  })
}

export function useRegenerateRecoveryCodes() {
  const refresh = useRefreshMe()
  return useMutation({
    mutationFn: (password: string) =>
      apiFetch<{ recoveryCodes: string[] }>("/api/v1/account/recovery-codes", {
        method: "POST",
        json: { password },
      }),
    onSuccess: refresh,
  })
}

/** E-mail de recuperação (ID-05): definir manda o link de confirmação ao novo endereço. */
export function useChangeEmail() {
  const refresh = useRefreshMe()
  return useMutation({
    mutationFn: (input: { email: string; password: string }) =>
      apiFetch<Account>("/api/v1/account/email", {
        method: "PUT",
        json: input,
      }),
    onSuccess: refresh,
  })
}

export function useRemoveEmail() {
  const refresh = useRefreshMe()
  return useMutation({
    mutationFn: (password: string) =>
      apiFetch<Account>("/api/v1/account/email", {
        method: "DELETE",
        json: { password },
      }),
    onSuccess: refresh,
  })
}

export function useRevokeSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string | null) =>
      apiFetch<unknown>(id ? `/api/v1/sessions/${id}` : "/api/v1/sessions", {
        method: "DELETE",
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: accountKeys.sessions }),
  })
}
