import { useEffect } from "react"
import { useNavigate } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"

import { serviceWorkerRegistration } from "@/lib/pwa"
import { realtime } from "@/lib/realtime"
import { notificationQueries } from "./api"
import { notificationsSupported, subscribePush } from "./push"

/**
 * Notificações com o app aberto (NT-01): o servidor já decidiu quem avisar e com que texto (silêncio, menções,
 * "ocultar conteúdo", trancadas); aqui só se mostra, quando a aba está escondida ou em outra conversa. A
 * mesma {@code tag} do push faz as duas notificações se substituírem. Também mantém o contador no ícone
 * (Badging API, NT-03) e confirma a assinatura de push quando a permissão já foi dada.
 */
export function useNotifications(unreadTotal: number) {
  const navigate = useNavigate()
  const push = useQuery(notificationQueries.push())

  useEffect(() => {
    if (
      notificationsSupported() &&
      Notification.permission === "granted" &&
      push.data?.enabled
    ) {
      void subscribePush(push.data.publicKey).catch(() => undefined)
    }
  }, [push.data])

  useEffect(() => {
    return realtime.subscribe((event) => {
      if (event.type !== "notify") {
        return
      }
      const watching =
        document.visibilityState === "visible" &&
        window.location.pathname === `/c/${event.conversationId}`
      if (
        watching ||
        !notificationsSupported() ||
        Notification.permission !== "granted"
      ) {
        return
      }
      void show(event, () =>
        navigate({
          to: "/c/$conversationId",
          params: { conversationId: event.conversationId },
        })
      )
    })
  }, [navigate])

  useEffect(() => {
    if (!("setAppBadge" in navigator)) {
      return
    }
    void (
      unreadTotal > 0
        ? navigator.setAppBadge(unreadTotal)
        : navigator.clearAppBadge()
    ).catch(() => undefined)
  }, [unreadTotal])
}

async function show(
  event: { title: string; body: string; tag: string; silent: boolean },
  open: () => void
) {
  const options = {
    body: event.body,
    tag: event.tag,
    silent: event.silent,
    icon: "/icons/icon-192.png",
  }
  // Com service worker, a notificação passa por ele (no Android o construtor Notification não existe).
  const registration = await serviceWorkerRegistration()
  if (registration) {
    await registration.showNotification(event.title, {
      ...options,
      badge: "/icons/badge-96.png",
      data: {
        conversationId: event.tag.replace(/^conversation-/, ""),
      },
    })
    return
  }
  const notification = new Notification(event.title, options)
  notification.onclick = () => {
    window.focus()
    open()
    notification.close()
  }
}
