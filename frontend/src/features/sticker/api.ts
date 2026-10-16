import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"
import { refreshQueries } from "@/lib/query-refresh"

export type Sticker = {
  id: string
  mediaId: string
  emoji: string | null
  favorite: boolean
}

export type StickerPack = {
  id: string
  name: string
  mine: boolean
  stickers: Sticker[]
}

export type GifResult = {
  id: string
  title: string
  width: number
  height: number
}

export const stickerKeys = {
  all: ["stickers"] as const,
  packs: ["stickers", "packs"] as const,
  favorites: ["stickers", "favorites"] as const,
  gifStatus: ["gifs", "status"] as const,
  gifs: (query: string) => ["gifs", "search", query] as const,
}

export const stickerQueries = {
  packs: () =>
    queryOptions({
      queryKey: stickerKeys.packs,
      queryFn: ({ signal }) =>
        apiFetch<StickerPack[]>("/api/v1/stickers/packs", { signal }),
    }),
  favorites: () =>
    queryOptions({
      queryKey: stickerKeys.favorites,
      queryFn: ({ signal }) =>
        apiFetch<Sticker[]>("/api/v1/stickers/favorites", { signal }),
    }),
  gifStatus: () =>
    queryOptions({
      queryKey: stickerKeys.gifStatus,
      queryFn: ({ signal }) =>
        apiFetch<{ enabled: boolean }>("/api/v1/gifs/status", { signal }),
      staleTime: 5 * 60_000,
    }),
  gifs: (query: string) =>
    queryOptions({
      queryKey: stickerKeys.gifs(query),
      queryFn: ({ signal }) =>
        apiFetch<GifResult[]>(`/api/v1/gifs?q=${encodeURIComponent(query)}`, {
          signal,
        }),
      staleTime: 10 * 60_000,
    }),
}

type StickerAction =
  | { action: "createPack"; name: string }
  | { action: "removePack"; packId: string }
  | { action: "add"; packId: string; mediaId: string; emoji?: string }
  | { action: "remove"; stickerId: string }
  | { action: "favorite"; stickerId: string; favorite: boolean }

/** Biblioteca de figurinhas (MS-10); o servidor confere quem é dono do pacote. */
export function useStickerAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: StickerAction): Promise<unknown> => {
      switch (input.action) {
        case "createPack":
          return apiFetch("/api/v1/stickers/packs", {
            method: "POST",
            json: { name: input.name },
          })
        case "removePack":
          return apiFetch(`/api/v1/stickers/packs/${input.packId}`, {
            method: "DELETE",
          })
        case "add":
          return apiFetch(`/api/v1/stickers/packs/${input.packId}/stickers`, {
            method: "POST",
            json: { mediaId: input.mediaId, emoji: input.emoji ?? null },
          })
        case "remove":
          return apiFetch(`/api/v1/stickers/${input.stickerId}`, {
            method: "DELETE",
          })
        case "favorite":
          return apiFetch(`/api/v1/stickers/${input.stickerId}/favorite`, {
            method: input.favorite ? "PUT" : "DELETE",
          })
      }
    },
    // Recarrega depois da busca em andamento: invalidar uma lista ainda sem dados reaproveita a primeira busca, que
    // saiu antes da mudança (um pacote criado com o diálogo recém-aberto não aparecia).
    onSuccess: () => refreshQueries(queryClient, stickerKeys.all),
  })
}

/** Guarda o GIF escolhido como mídia própria; devolve o id para anexar à mensagem. */
export function attachGif(id: string) {
  return apiFetch<{ id: string; contentType: string; size: number }>(
    `/api/v1/gifs/${id}/attach`,
    { method: "POST" }
  )
}
