/// <reference lib="webworker" />
import {
  cleanupOutdatedCaches,
  createHandlerBoundToURL,
  precacheAndRoute,
} from "workbox-precaching"
import { NavigationRoute, registerRoute } from "workbox-routing"

declare let self: ServiceWorkerGlobalScope

/**
 * Service worker do Papinho (NT-04, NT-01). Só guarda o próprio app (HTML, JS, CSS, ícones) gerado no build:
 * nenhuma resposta da API, do WebSocket ou de mídia passa pelo Cache Storage, porque essas rotas nem são
 * registradas aqui e vão direto para a rede. Atualização controlada: a versão nova espera o usuário escolher
 * "Atualizar" (mensagem SKIP_WAITING), nunca troca sozinha.
 */
precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

// Rotas do SPA abrem o index.html guardado (funciona offline); as do servidor nunca.
registerRoute(
  new NavigationRoute(createHandlerBoundToURL("/index.html"), {
    denylist: [
      /^\/api\//,
      /^\/ws/,
      /^\/setup/,
      /^\/actuator/,
      /^\/webauthn/,
      /^\/login\/webauthn/,
    ],
  })
)

self.addEventListener("message", (event) => {
  if (event.data?.type === "SKIP_WAITING") {
    void self.skipWaiting()
  }
})

/** Conteúdo do push (já decidido pelo servidor: sem texto quando a pessoa pediu ou a conversa é trancada). */
type PushPayload = {
  title: string
  body: string
  /** Mesma tag da notificação mostrada pela página: as duas se substituem em vez de duplicar. */
  tag: string
  conversationId: string | null
  silent: boolean
  /** Total de não lidas, para o contador no ícone (Badging API). */
  badge: number | null
}

self.addEventListener("push", (event) => {
  let payload: PushPayload
  try {
    payload = event.data?.json() as PushPayload
  } catch {
    return
  }
  event.waitUntil(show(payload))
})

async function show(payload: PushPayload) {
  if (payload.badge !== null && "setAppBadge" in self.navigator) {
    await (
      payload.badge > 0
        ? self.navigator.setAppBadge(payload.badge)
        : self.navigator.clearAppBadge()
    ).catch(() => undefined)
  }
  const windows = await self.clients.matchAll({
    type: "window",
    includeUncontrolled: true,
  })
  // Com a conversa aberta e em foco, a própria página já mostra a mensagem.
  const watching = windows.some(
    (client) =>
      client.focused &&
      payload.conversationId !== null &&
      new URL(client.url).pathname === `/c/${payload.conversationId}`
  )
  if (watching) {
    return
  }
  await self.registration.showNotification(payload.title, {
    body: payload.body,
    tag: payload.tag,
    icon: "/icons/icon-192.png",
    badge: "/icons/badge-96.png",
    silent: payload.silent,
    data: { conversationId: payload.conversationId },
  })
}

self.addEventListener("notificationclick", (event) => {
  event.notification.close()
  const conversationId = (
    event.notification.data as { conversationId: string | null } | null
  )?.conversationId
  const path = conversationId ? `/c/${conversationId}` : "/"
  event.waitUntil(open(path))
})

async function open(path: string) {
  const windows = await self.clients.matchAll({
    type: "window",
    includeUncontrolled: true,
  })
  const existing = windows.find(
    (client) => new URL(client.url).origin === self.location.origin
  )
  if (existing) {
    // A página navega pelo próprio roteador, sem recarregar o app.
    existing.postMessage({ type: "OPEN", path })
    await existing.focus()
    return
  }
  await self.clients.openWindow(path)
}
