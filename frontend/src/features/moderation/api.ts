import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"

export type PersonRef = { id: string; username: string; active: boolean }

export type CopiedMessage = {
  sender: PersonRef | null
  body: string
  sentAt: string
  fromReported: boolean
}

export type Report = {
  id: string
  reporter: PersonRef | null
  reported: PersonRef | null
  reason: string | null
  status: "OPEN" | "RESOLVED" | "DISMISSED"
  createdAt: string
  resolvedAt: string | null
  messages: CopiedMessage[]
}

export type ReportPage = {
  items: Report[]
  page: number
  totalItems: number
  totalPages: number
  openCount: number
}

export const moderationKeys = {
  reports: ["admin", "reports"] as const,
  page: (open: boolean, page: number) =>
    ["admin", "reports", open, page] as const,
}

export const moderationQueries = {
  reports: (open: boolean, page: number) =>
    queryOptions({
      queryKey: moderationKeys.page(open, page),
      queryFn: ({ signal }) =>
        apiFetch<ReportPage>(
          `/api/v1/admin/reports?open=${open}&page=${page}&size=20`,
          { signal }
        ),
      placeholderData: keepPreviousData,
    }),
}

/** Denuncia a outra pessoa da conversa (CT-04); com `block`, bloqueia junto. */
export function useReport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      conversationId: string
      reason: string
      block: boolean
    }) =>
      apiFetch<void>("/api/v1/reports", {
        method: "POST",
        json: {
          conversationId: input.conversationId,
          reason: input.reason.trim() || null,
          block: input.block,
        },
      }),
    onSuccess: (_data, input) => {
      if (input.block) {
        void queryClient.invalidateQueries({ queryKey: ["contacts"] })
        void queryClient.invalidateQueries({ queryKey: ["conversations"] })
      }
    },
  })
}

export function useCloseReport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      action,
    }: {
      id: string
      action: "resolve" | "dismiss"
    }) =>
      apiFetch<void>(`/api/v1/admin/reports/${id}/${action}`, {
        method: "POST",
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: moderationKeys.reports }),
  })
}
