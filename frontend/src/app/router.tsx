import { createRouter } from "@tanstack/react-router"

import { queryClient } from "@/lib/query-client"
import { routeTree } from "@/routeTree.gen"

export const router = createRouter({
  routeTree,
  context: { queryClient },
  defaultPreload: "intent",
  scrollRestoration: true,
})

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router
  }
  interface HistoryState {
    /** Termo da busca global que abriu a conversa (GR-06): fica no histórico do navegador, fora da URL e dos logs. */
    searchQuery?: string
  }
}
