import { queryOptions } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"

export type EmbedProvider = "YOUTUBE" | "VIMEO" | "SPOTIFY"

export type LinkPreview = {
  url: string
  available: boolean
  title: string | null
  description: string | null
  siteName: string | null
  /** Miniatura servida pelo próprio Papinho, nunca a URL externa. */
  imageUrl: string | null
  embedProvider: EmbedProvider | null
  embedSrc: string | null
}

export const linkPreviewQueries = {
  preview: (url: string) =>
    queryOptions({
      queryKey: ["link-preview", url] as const,
      queryFn: ({ signal }) =>
        apiFetch<LinkPreview>(
          `/api/v1/link-previews?url=${encodeURIComponent(url)}`,
          { signal }
        ),
      staleTime: 60 * 60_000,
      gcTime: 60 * 60_000,
      retry: false,
    }),
}
