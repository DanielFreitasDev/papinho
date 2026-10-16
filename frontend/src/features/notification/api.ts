import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"

/** Preferências de notificação (NT-03). */
export type NotificationPreferences = {
  enabled: boolean
  direct: boolean
  groups: boolean
  /** Menções nos grupos, mesmo com os grupos desligados. */
  mentions: boolean
  sound: boolean
}

export const notificationKeys = {
  preferences: ["notification-preferences"] as const,
  push: ["notification-push"] as const,
}

export const notificationQueries = {
  preferences: () =>
    queryOptions({
      queryKey: notificationKeys.preferences,
      queryFn: ({ signal }) =>
        apiFetch<NotificationPreferences>("/api/v1/notifications/preferences", {
          signal,
        }),
    }),
  push: () =>
    queryOptions({
      queryKey: notificationKeys.push,
      queryFn: ({ signal }) =>
        apiFetch<{ enabled: boolean; publicKey: string | null }>(
          "/api/v1/notifications/push",
          { signal }
        ),
      staleTime: Infinity,
    }),
}

export function useChangeNotificationPreferences() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (next: NotificationPreferences) =>
      apiFetch<NotificationPreferences>("/api/v1/notifications/preferences", {
        method: "PUT",
        json: next,
      }),
    onSuccess: (saved) =>
      queryClient.setQueryData(notificationKeys.preferences, saved),
  })
}
