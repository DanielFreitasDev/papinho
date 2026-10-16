import { apiFetch } from "@/lib/api-client"
import { onLogout } from "@/lib/logout-cleanup"
import { serviceWorkerRegistration } from "@/lib/pwa"

/** O navegador sabe notificar? (Notification API; o push precisa também de service worker e PushManager.) */
export function notificationsSupported() {
  return typeof window !== "undefined" && "Notification" in window
}

export function pushSupported() {
  return (
    notificationsSupported() &&
    "serviceWorker" in navigator &&
    "PushManager" in window
  )
}

/** iOS/iPadOS fora do app instalado: lá o push só existe com o PWA na tela inicial (NT-02). */
export function iosNeedsInstall() {
  const ios =
    /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1)
  const standalone =
    window.matchMedia("(display-mode: standalone)").matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  return ios && !standalone
}

/**
 * Pede a permissão (só depois de um clique, nunca ao abrir o app) e, havendo service worker, assina o Web Push
 * com a chave VAPID da instância e manda a assinatura ao servidor (NT-01).
 */
export async function enableNotifications(publicKey: string | null) {
  const permission = await Notification.requestPermission()
  if (permission !== "granted") {
    return permission
  }
  await subscribePush(publicKey)
  return permission
}

/** Assina de novo (ou confirma) quando a permissão já existe; nada acontece sem service worker. */
export async function subscribePush(publicKey: string | null) {
  const registration = await serviceWorkerRegistration()
  if (!registration || !publicKey || !pushSupported()) {
    return false
  }
  const ready = await navigator.serviceWorker.ready
  const subscription =
    (await ready.pushManager.getSubscription()) ??
    (await ready.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: base64UrlToBytes(publicKey),
    }))
  await apiFetch<void>("/api/v1/notifications/push/subscriptions", {
    method: "POST",
    json: subscription.toJSON(),
  })
  return true
}

/** Ao sair da conta este navegador para de receber push dela. */
export async function unsubscribePush() {
  if (!pushSupported()) {
    return
  }
  const registration = await navigator.serviceWorker.getRegistration()
  const subscription = await registration?.pushManager.getSubscription()
  if (!subscription) {
    return
  }
  await apiFetch<void>("/api/v1/notifications/push/subscriptions", {
    method: "DELETE",
    json: { endpoint: subscription.endpoint },
  }).catch(() => undefined)
  await subscription.unsubscribe()
}

onLogout(unsubscribePush)

function base64UrlToBytes(value: string) {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/")
  const binary = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="))
  return Uint8Array.from(binary, (char) => char.charCodeAt(0))
}
