import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import type { RegistrationMode } from "@/features/auth"
import { apiFetch } from "@/lib/api-client"

export type InstanceSettings = {
  registrationMode: RegistrationMode
  usernameChangeCooldownDays: number
  messageEditWindowMinutes: number
  messageDeleteWindowHours: number
  userQuotaMegabytes: number
  breachedPasswordCheck: boolean
}

export type AdminUser = {
  id: string
  username: string
  displayName: string
  admin: boolean
  enabled: boolean
  createdAt: string
}

export type UserPage = {
  items: AdminUser[]
  page: number
  totalItems: number
  totalPages: number
}

export const adminKeys = {
  settings: ["admin", "settings"] as const,
  users: (query: string, page: number) =>
    ["admin", "users", query, page] as const,
}

export const adminQueries = {
  settings: () =>
    queryOptions({
      queryKey: adminKeys.settings,
      queryFn: ({ signal }) =>
        apiFetch<InstanceSettings>("/api/v1/admin/settings", { signal }),
    }),
  users: (query: string, page: number) =>
    queryOptions({
      queryKey: adminKeys.users(query, page),
      queryFn: ({ signal }) =>
        apiFetch<UserPage>(
          `/api/v1/admin/users?query=${encodeURIComponent(query)}&page=${page}&size=20`,
          { signal }
        ),
      placeholderData: keepPreviousData,
    }),
}

export function useUpdateSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (settings: InstanceSettings) =>
      apiFetch<InstanceSettings>("/api/v1/admin/settings", {
        method: "PUT",
        json: settings,
      }),
    onSuccess: (data) => {
      queryClient.setQueryData(adminKeys.settings, data)
      void queryClient.invalidateQueries({ queryKey: ["instance"] })
    },
  })
}

export function useUserAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      action,
    }: {
      id: string
      action: "disable" | "enable" | "reset-password"
    }) =>
      apiFetch<{ temporaryPassword?: string } | undefined>(
        `/api/v1/admin/users/${id}/${action}`,
        {
          method: "POST",
        }
      ),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
  })
}
