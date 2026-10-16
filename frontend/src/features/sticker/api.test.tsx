import type { ReactNode } from "react"
import { describe, expect, it } from "vitest"
import { delay, http, HttpResponse } from "msw"
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
} from "@tanstack/react-query"
import { act, renderHook, waitFor } from "@testing-library/react"

import { server } from "@/test/msw/server"
import { stickerQueries, useStickerAction, type StickerPack } from "./api"

describe("useStickerAction", () => {
  it("pacote criado durante a primeira busca da lista aparece, sem reaproveitar a busca antiga", async () => {
    let packs: StickerPack[] = []
    let gets = 0
    server.use(
      http.get("/api/v1/stickers/packs", async () => {
        gets++
        // A resposta reflete o banco no momento em que o pedido chegou, como no servidor.
        const snapshot = [...packs]
        await delay(150)
        return HttpResponse.json(snapshot)
      }),
      http.post("/api/v1/stickers/packs", () => {
        packs = [{ id: "p1", name: "Gatos", mine: true, stickers: [] }]
        return HttpResponse.json(packs[0], { status: 201 })
      })
    )
    const client = new QueryClient({
      defaultOptions: {
        queries: { retry: false, staleTime: 30_000 },
        mutations: { retry: false },
      },
    })
    function Wrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      )
    }
    const { result } = renderHook(
      () => ({
        packs: useQuery(stickerQueries.packs()),
        action: useStickerAction(),
      }),
      { wrapper: Wrapper }
    )

    await act(() =>
      result.current.action.mutateAsync({
        action: "createPack",
        name: "Gatos",
      })
    )

    await waitFor(() =>
      expect(result.current.packs.data?.map((pack) => pack.name)).toEqual([
        "Gatos",
      ])
    )
    expect(gets).toBe(2)
  })
})
