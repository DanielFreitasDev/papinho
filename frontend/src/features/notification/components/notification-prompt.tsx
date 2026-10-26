import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { BellRingIcon, XIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { notificationQueries } from "../api"
import {
  enableNotifications,
  iosNeedsInstall,
  notificationsSupported,
} from "../push"

const DISMISSED = "papinho.notification-prompt-dismissed"

/**
 * Faixa no topo da lista, como no WhatsApp Web: enquanto o navegador não decidiu a permissão, oferece ativar as
 * notificações do sistema. Sem ela, nenhum aviso chega (a permissão só pode ser pedida depois de um clique).
 * Fechar a faixa vale para este navegador; Configurações > Notificações continua com o botão.
 */
export function NotificationPrompt() {
  const { t } = useTranslation()
  const push = useQuery(notificationQueries.push())
  const [hidden, setHidden] = useState(
    () =>
      !notificationsSupported() ||
      Notification.permission !== "default" ||
      iosNeedsInstall() ||
      readDismissed()
  )
  if (hidden || push.isPending) {
    return null
  }

  async function enable() {
    try {
      await enableNotifications(push.data?.publicKey ?? null)
      setHidden(Notification.permission !== "default")
    } catch (error) {
      setHidden(Notification.permission !== "default")
      toast.add({
        title: errorMessage(error, t("common.genericError")),
        type: "error",
      })
    }
  }

  return (
    <div className="mx-(--spacing-gutter) mb-1 flex items-center gap-3 rounded-lg bg-primary/10 py-2 pr-1 pl-3 text-sm">
      <BellRingIcon
        aria-hidden="true"
        className="size-5 shrink-0 text-primary"
      />
      <span className="min-w-0 flex-1 font-medium">
        {t("notifications.promptTitle")}
      </span>
      <Button size="sm" onClick={() => void enable()}>
        {t("notifications.enableShort")}
      </Button>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label={t("common.close")}
        onClick={() => {
          writeDismissed()
          setHidden(true)
        }}
      >
        <XIcon />
      </Button>
    </div>
  )
}

function readDismissed() {
  try {
    return localStorage.getItem(DISMISSED) === "1"
  } catch {
    return false
  }
}

function writeDismissed() {
  try {
    localStorage.setItem(DISMISSED, "1")
  } catch {
    // Sem armazenamento a faixa só some até recarregar.
  }
}
