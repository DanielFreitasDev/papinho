import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { apiFetch, ApiError } from "@/lib/api-client"
import { transcribeInBrowser } from "./browser-whisper"

export type Transcript = { mediaId: string; text: string; source: string }

export type Preference = { provider: string; auto: boolean }

export type Options = {
  /** Id do provedor padrão da instância ou "BROWSER". */
  instanceDefault: string
  providers: { id: string; name: string }[]
  preference: Preference
}

export type ProviderKind = "GROQ" | "WHISPER" | "OPENAI" | "CUSTOM"

export type AdminProvider = {
  id: string
  kind: ProviderKind
  name: string
  baseUrl: string
  model: string
  hotwords: string | null
  maxBytes: number
  enabled: boolean
  hasKey: boolean
}

export type AdminView = {
  defaultProviderId: string | null
  providers: AdminProvider[]
}

export const transcriptionKeys = {
  transcript: (mediaId: string) => ["transcript", mediaId] as const,
  options: ["transcription", "options"] as const,
  admin: ["admin", "transcription"] as const,
}

export const transcriptionQueries = {
  transcript: (mediaId: string) =>
    queryOptions({
      queryKey: transcriptionKeys.transcript(mediaId),
      queryFn: ({ signal }) =>
        apiFetch<Transcript | undefined>(
          `/api/v1/media/${mediaId}/transcript`,
          { signal }
        ).then((value) => value ?? null),
      staleTime: Infinity,
    }),
  options: () =>
    queryOptions({
      queryKey: transcriptionKeys.options,
      queryFn: ({ signal }) =>
        apiFetch<Options>("/api/v1/transcription/options", { signal }),
      staleTime: 5 * 60_000,
    }),
  admin: () =>
    queryOptions({
      queryKey: transcriptionKeys.admin,
      queryFn: ({ signal }) =>
        apiFetch<AdminView>("/api/v1/admin/transcription", { signal }),
    }),
}

/**
 * Transcreve (AU-03): pede ao servidor; se o provedor for o navegador (409), transcreve aqui (AU-04) e envia o
 * texto para o cache dos demais.
 */
export function useTranscribe(mediaId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (onProgress: (fraction: number) => void) => {
      try {
        return await apiFetch<Transcript>(
          `/api/v1/media/${mediaId}/transcript`,
          { method: "POST" }
        )
      } catch (error) {
        if (!(error instanceof ApiError && error.status === 409)) {
          throw error
        }
      }
      const text = await transcribeInBrowser(
        `/api/v1/media/${mediaId}/content`,
        null,
        onProgress
      )
      return apiFetch<Transcript>(`/api/v1/media/${mediaId}/transcript`, {
        method: "PUT",
        json: { text },
      })
    },
    onSuccess: (transcript) =>
      queryClient.setQueryData(
        transcriptionKeys.transcript(mediaId),
        transcript
      ),
  })
}

export function useSavePreference() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (preference: Preference) =>
      apiFetch<Preference>("/api/v1/transcription/preference", {
        method: "PUT",
        json: preference,
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: transcriptionKeys.options }),
  })
}

export function useAdminTranscription() {
  const queryClient = useQueryClient()
  const invalidate = () =>
    queryClient
      .invalidateQueries({ queryKey: ["transcription"] })
      .then(() =>
        queryClient.invalidateQueries({ queryKey: transcriptionKeys.admin })
      )
  return {
    create: useMutation({
      mutationFn: (kind: ProviderKind) =>
        apiFetch<AdminProvider>("/api/v1/admin/transcription/providers", {
          method: "POST",
          json: { kind },
        }),
      onSuccess: invalidate,
    }),
    update: useMutation({
      mutationFn: ({
        id,
        ...body
      }: Omit<AdminProvider, "kind" | "hasKey"> & { apiKey: string | null }) =>
        apiFetch<AdminProvider>(`/api/v1/admin/transcription/providers/${id}`, {
          method: "PUT",
          json: body,
        }),
      onSuccess: invalidate,
    }),
    remove: useMutation({
      mutationFn: (id: string) =>
        apiFetch<void>(`/api/v1/admin/transcription/providers/${id}`, {
          method: "DELETE",
        }),
      onSuccess: invalidate,
    }),
    setDefault: useMutation({
      mutationFn: (providerId: string | null) =>
        apiFetch<void>("/api/v1/admin/transcription/default", {
          method: "PUT",
          json: { providerId },
        }),
      onSuccess: invalidate,
    }),
    health: useMutation({
      mutationFn: (id: string) =>
        apiFetch<{ healthy: boolean }>(
          `/api/v1/admin/transcription/providers/${id}/health`,
          { method: "POST" }
        ),
    }),
  }
}
