import { createRouter } from "@tanstack/react-router"

import { queryClient } from "@/lib/query-client"
import { routeTree } from "@/routeTree.gen"

/** Profundidade da rota: a lista é 0; conversa e configurações ficam "dentro" dela. */
function depth(pathname: string) {
  return pathname.split("/").filter(Boolean).length
}

export const router = createRouter({
  routeTree,
  context: { queryClient },
  defaultPreload: "intent",
  scrollRestoration: true,
  // Troca de tela animada pelo navegador (view transition): o tipo diz a direção e o index.css escolhe o efeito
  // (no celular a tela desliza; no desktop só o painel da conversa troca). Só quando o caminho muda.
  defaultViewTransition: {
    types: ({ fromLocation, toLocation, pathChanged }) => {
      if (!fromLocation || !pathChanged) {
        return false
      }
      const from = depth(fromLocation.pathname)
      const to = depth(toLocation.pathname)
      return [to > from ? "forward" : to < from ? "back" : "swap"]
    },
  },
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
