import { QueryClient, QueryObserver } from "@tanstack/react-query"
import { describe, expect, it } from "vitest"

import { refreshQueries } from "./query-refresh"

/**
 * Lista de conversas já carregada e observada (como a tela faria), com um servidor que responde quando o teste
 * manda; cada busca guarda o AbortSignal, que no HTTP/2 vira RST_STREAM quando abortado.
 */
async function loadedConversationList() {
  const queryClient = new QueryClient()
  const fetches: { signal: AbortSignal; release: () => void }[] = []
  let version = 0
  const stop = new QueryObserver(queryClient, {
    queryKey: ["conversations", "list"],
    queryFn: ({ signal }) =>
      new Promise<number>((resolve) => {
        fetches.push({ signal, release: () => resolve(++version) })
      }),
  }).subscribe(() => undefined)
  fetches[0].release()
  await expect.poll(() => queryClient.isFetching()).toBe(0)
  return { queryClient, fetches, stop }
}

describe("refreshQueries", () => {
  it("o invalidateQueries padrão aborta a busca em andamento a cada aviso (rajada de RST_STREAM)", async () => {
    const { queryClient, fetches, stop } = await loadedConversationList()
    for (let event = 0; event < 5; event++) {
      void queryClient.invalidateQueries({ queryKey: ["conversations"] })
    }
    await expect.poll(() => fetches.length).toBe(6)
    expect(fetches.filter((fetch) => fetch.signal.aborted)).toHaveLength(4)
    stop()
  })

  it("uma rajada de avisos não aborta nada e termina com uma busca feita depois do último aviso", async () => {
    const { queryClient, fetches, stop } = await loadedConversationList()
    const first = refreshQueries(queryClient, ["conversations"])
    await expect.poll(() => fetches.length).toBe(2)
    // Mais avisos enquanto a busca roda: ela pode ter lido o banco antes deles; vem uma rodada só no fim.
    const burst = Array.from({ length: 4 }, () =>
      refreshQueries(queryClient, ["conversations"])
    )
    fetches[1].release()
    await expect.poll(() => fetches.length).toBe(3)
    void refreshQueries(queryClient, ["conversations"])
    fetches[2].release()
    await expect.poll(() => fetches.length).toBe(4)
    fetches[3].release()
    await Promise.all([first, ...burst])

    expect(fetches.some((fetch) => fetch.signal.aborted)).toBe(false)
    expect(fetches).toHaveLength(4)
    expect(queryClient.getQueryData(["conversations", "list"])).toBe(4)
    stop()
  })

  it("espera a busca que já estava rodando e busca de novo depois dela", async () => {
    const { queryClient, fetches, stop } = await loadedConversationList()
    // Uma busca de outra origem (a tela voltou ao foco) já estava rodando quando o aviso chegou.
    void queryClient.refetchQueries({ queryKey: ["conversations"] })
    await expect.poll(() => fetches.length).toBe(2)
    const refresh = refreshQueries(queryClient, ["conversations"])
    fetches[1].release()
    await expect.poll(() => fetches.length).toBe(3)
    fetches[2].release()
    await refresh
    expect(fetches.some((fetch) => fetch.signal.aborted)).toBe(false)
    expect(queryClient.getQueryData(["conversations", "list"])).toBe(3)
    stop()
  })
})
