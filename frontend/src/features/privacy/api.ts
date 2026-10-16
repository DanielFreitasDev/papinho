import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"
import { setUnlockToken } from "@/lib/unlock-token"
import type {
  PrivacyItem,
  PrivacyState,
  PrivacySettingsChange,
  ScreenLock,
  ScreenLockChange,
  Unlocked,
} from "./types"

export const privacyKeys = {
  settings: ["privacy"] as const,
  screenLock: ["privacy", "screen-lock"] as const,
  lockedCount: ["privacy", "locked-count"] as const,
}

export const privacyQueries = {
  settings: () =>
    queryOptions({
      queryKey: privacyKeys.settings,
      queryFn: ({ signal }) =>
        apiFetch<PrivacyState>("/api/v1/privacy", { signal }),
      staleTime: 60_000,
    }),
  screenLock: () =>
    queryOptions({
      queryKey: privacyKeys.screenLock,
      queryFn: ({ signal }) =>
        apiFetch<ScreenLock>("/api/v1/privacy/screen-lock", { signal }),
      staleTime: 60_000,
    }),
  lockedCount: () =>
    queryOptions({
      queryKey: privacyKeys.lockedCount,
      queryFn: ({ signal }) =>
        apiFetch<{ count: number }>("/api/v1/privacy/locked-conversations", {
          signal,
        }),
    }),
}

/** Altera configurações; a resposta já traz o estado novo (atualização otimista do modo privado). */
export function useUpdatePrivacy() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (change: PrivacySettingsChange) =>
      apiFetch<PrivacyState>("/api/v1/privacy", {
        method: "PATCH",
        json: change,
      }),
    onMutate: async (change) => {
      await queryClient.cancelQueries({ queryKey: privacyKeys.settings })
      const previous = queryClient.getQueryData<PrivacyState>(
        privacyKeys.settings
      )
      if (previous && change.privateMode !== undefined) {
        queryClient.setQueryData<PrivacyState>(privacyKeys.settings, {
          ...previous,
          settings: {
            ...previous.settings,
            blur: {
              ...previous.settings.blur,
              privateMode: change.privateMode,
            },
          },
        })
      }
      return { previous }
    },
    onError: (_error, _change, context) => {
      if (context?.previous) {
        queryClient.setQueryData(privacyKeys.settings, context.previous)
      }
    },
    onSuccess: (state) => {
      queryClient.setQueryData(privacyKeys.settings, state)
      // Visibilidade e reciprocidade mudam o que vejo dos outros.
      void queryClient.invalidateQueries({ queryKey: ["presence"] })
      void queryClient.invalidateQueries({ queryKey: ["conversations"] })
    },
  })
}

export function useReplaceExceptions() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ item, userIds }: { item: PrivacyItem; userIds: string[] }) =>
      apiFetch<string[]>(`/api/v1/privacy/exceptions/${item}`, {
        method: "PUT",
        json: { userIds },
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: privacyKeys.settings }),
  })
}

export function useSaveScreenLock() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (change: ScreenLockChange) =>
      apiFetch<ScreenLock>("/api/v1/privacy/screen-lock", {
        method: "PUT",
        json: change,
      }),
    onSuccess: (lock) => queryClient.setQueryData(privacyKeys.screenLock, lock),
  })
}

export function useRemoveScreenLock() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (secret: string) =>
      apiFetch<void>("/api/v1/privacy/screen-lock", {
        method: "DELETE",
        json: { secret },
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: privacyKeys.screenLock }),
  })
}

export function unlockScreen(secret: string) {
  return apiFetch<void>("/api/v1/privacy/screen-lock/unlock", {
    method: "POST",
    json: { secret },
  })
}

/** Abre a pasta (ou uma conversa) trancada com o código; o token passa a ir em toda requisição. */
export function useUnlockConversations() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (code: string) =>
      apiFetch<Unlocked>("/api/v1/privacy/locked-conversations/unlock", {
        method: "POST",
        json: { code },
      }),
    onSuccess: (unlocked) => {
      setUnlockToken(unlocked.token)
      void queryClient.invalidateQueries({ queryKey: ["conversations"] })
      void queryClient.invalidateQueries({ queryKey: ["messages"] })
    },
  })
}

/** Tranca a conversa com o código (o mesmo de outra conversa vira código compartilhado). */
export function useLockConversation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      conversationId,
      code,
    }: {
      conversationId: string
      code: string
    }) =>
      apiFetch<Unlocked>(
        `/api/v1/privacy/locked-conversations/${conversationId}`,
        { method: "PUT", json: { code } }
      ),
    onSuccess: (unlocked) => {
      setUnlockToken(unlocked.token)
      void queryClient.invalidateQueries({ queryKey: ["conversations"] })
      void queryClient.invalidateQueries({ queryKey: privacyKeys.lockedCount })
    },
  })
}

/** Tira a conversa da pasta trancada (exige que ela esteja aberta). */
export function useUnlockPermanently() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (conversationId: string) =>
      apiFetch<void>(`/api/v1/privacy/locked-conversations/${conversationId}`, {
        method: "DELETE",
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["conversations"] })
      void queryClient.invalidateQueries({ queryKey: privacyKeys.lockedCount })
    },
  })
}

/** Fecha a pasta: o token deixa de valer e o conteúdo carregado sai da memória. */
export function useRelockAll() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (conversationIds: string[]) => {
      await apiFetch<void>("/api/v1/privacy/locked-conversations/lock-all", {
        method: "POST",
      }).catch(() => undefined)
      return conversationIds
    },
    onSettled: (conversationIds) => {
      setUnlockToken(null)
      conversationIds?.forEach((id) => {
        queryClient.removeQueries({ queryKey: ["messages", id] })
        queryClient.removeQueries({ queryKey: ["conversations", "detail", id] })
      })
      queryClient.removeQueries({ queryKey: ["conversations", "locked"] })
      void queryClient.invalidateQueries({ queryKey: ["conversations"] })
    },
  })
}
