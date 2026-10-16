import { Workbox } from "workbox-window"

import { toast } from "@/components/ui/toast"
import i18n from "@/lib/i18n"

let registration: Promise<ServiceWorkerRegistration | undefined> | null = null

/**
 * Registra o service worker (só no build de produção) e avisa quando há versão nova (NT-04). A troca só
 * acontece quando a pessoa escolhe "Atualizar": nada de skipWaiting cego, que poderia misturar versões no meio
 * de uma conversa.
 */
export function registerServiceWorker(onOpen: (path: string) => void) {
  if (!import.meta.env.PROD || !("serviceWorker" in navigator)) {
    return
  }
  const workbox = new Workbox("/sw.js", { scope: "/" })
  workbox.addEventListener("waiting", () => {
    toast.add({
      title: i18n.t("pwa.updateTitle"),
      description: i18n.t("pwa.updateDescription"),
      timeout: 0,
      actionProps: {
        children: i18n.t("pwa.update"),
        onClick: () => {
          workbox.addEventListener("controlling", () =>
            window.location.reload()
          )
          workbox.messageSkipWaiting()
        },
      },
    })
  })
  // Clique numa notificação com o app já aberto: abre a conversa sem recarregar.
  navigator.serviceWorker.addEventListener("message", (event) => {
    const data = event.data as { type?: string; path?: string } | null
    if (data?.type === "OPEN" && typeof data.path === "string") {
      onOpen(data.path)
    }
  })
  registration = workbox.register()
}

/** Registro do service worker, quando há (para o Web Push e as notificações). */
export async function serviceWorkerRegistration() {
  if (!registration) {
    return undefined
  }
  return (await registration) ?? undefined
}
