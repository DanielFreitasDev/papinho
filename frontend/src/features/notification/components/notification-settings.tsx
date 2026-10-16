import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { BellRingIcon, InfoIcon, SmartphoneIcon } from "lucide-react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import { SettingsSection } from "@/features/account"
import { errorMessage } from "@/lib/api-client"
import {
  notificationQueries,
  useChangeNotificationPreferences,
  type NotificationPreferences,
} from "../api"
import {
  enableNotifications,
  iosNeedsInstall,
  notificationsSupported,
  pushSupported,
} from "../push"

const TOGGLES = ["enabled", "direct", "groups", "mentions", "sound"] as const

/**
 * Configurações > Notificações (NT-01, NT-02, NT-03): a permissão do navegador só é pedida pelo botão; os
 * interruptores valem para todos os dispositivos. Avisa que o push depende de internet e, no iOS, do app
 * instalado na tela inicial.
 */
export function NotificationSettings() {
  const { t } = useTranslation()
  const preferences = useQuery(notificationQueries.preferences())
  const push = useQuery(notificationQueries.push())
  const change = useChangeNotificationPreferences()
  const supported = notificationsSupported()
  const [permission, setPermission] = useState<NotificationPermission>(
    supported ? Notification.permission : "denied"
  )

  async function enable() {
    try {
      setPermission(await enableNotifications(push.data?.publicKey ?? null))
    } catch (error) {
      toast.add({
        title: errorMessage(error, t("common.genericError")),
        type: "error",
      })
    }
  }

  function toggle(key: keyof NotificationPreferences, value: boolean) {
    if (!preferences.data) {
      return
    }
    change.mutate(
      { ...preferences.data, [key]: value },
      {
        onSuccess: () => toast.add({ title: t("common.saved") }),
        onError: (error) =>
          toast.add({
            title: errorMessage(error, t("common.genericError")),
            type: "error",
          }),
      }
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <SettingsSection
        title={t("notifications.browserTitle")}
        description={t("notifications.browserDescription")}
      >
        <div className="flex flex-col gap-3">
          {!supported ? (
            <p className="text-sm text-muted-foreground">
              {t("notifications.unsupported")}
            </p>
          ) : permission === "granted" ? (
            <p className="flex items-center gap-2 text-sm" role="status">
              <BellRingIcon aria-hidden="true" className="size-4" />
              {t("notifications.allowed")}
            </p>
          ) : permission === "denied" ? (
            <p className="text-sm text-muted-foreground" role="status">
              {t("notifications.denied")}
            </p>
          ) : (
            <Button className="self-start" onClick={() => void enable()}>
              <BellRingIcon />
              {t("notifications.enable")}
            </Button>
          )}
          <Alert>
            <InfoIcon />
            <AlertTitle>{t("notifications.internetTitle")}</AlertTitle>
            <AlertDescription>
              {t("notifications.internetDescription")}
            </AlertDescription>
          </Alert>
          {iosNeedsInstall() ? (
            <Alert>
              <SmartphoneIcon />
              <AlertTitle>{t("notifications.iosTitle")}</AlertTitle>
              <AlertDescription>
                {t("notifications.iosDescription")}
              </AlertDescription>
            </Alert>
          ) : null}
          {supported && !pushSupported() ? (
            <p className="text-sm text-muted-foreground">
              {t("notifications.pushUnavailable")}
            </p>
          ) : null}
        </div>
      </SettingsSection>
      <SettingsSection
        title={t("notifications.typesTitle")}
        description={t("notifications.typesDescription")}
      >
        {preferences.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : preferences.data ? (
          <ul className="flex flex-col divide-y">
            {TOGGLES.map((key) => (
              <li key={key}>
                <label className="flex min-h-12 items-center justify-between gap-3 py-1 text-sm">
                  <span className="flex flex-col">
                    {t(`notifications.toggle.${key}`)}
                    <span className="text-xs text-muted-foreground">
                      {t(`notifications.toggleHint.${key}`)}
                    </span>
                  </span>
                  <Switch
                    checked={preferences.data[key]}
                    disabled={
                      change.isPending ||
                      (key !== "enabled" && !preferences.data.enabled)
                    }
                    onCheckedChange={(value) => toggle(key, value)}
                  />
                </label>
              </li>
            ))}
          </ul>
        ) : null}
        <p className="mt-3 text-xs text-muted-foreground">
          {t("notifications.perConversation")}
        </p>
      </SettingsSection>
    </div>
  )
}
