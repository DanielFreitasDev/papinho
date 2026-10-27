import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import { SettingsSection } from "@/features/account"
import { useMediaQuery } from "@/hooks/use-media-query"
import { errorMessage } from "@/lib/api-client"
import { enterSends, setChatPrefs, useChatPrefs } from "@/lib/chat-prefs"
import { playSendSound } from "@/lib/sounds"
import { chatQueries, useChangePreferences } from "../api"
import { DisappearingSelect } from "./disappearing-select"
import { FolderSettings, KeepArchivedSetting } from "./folder-settings"

/**
 * Configurações > Conversas: digitação e envio (como no WhatsApp), timer padrão (MS-07), listas personalizadas e
 * arquivadas (GR-05).
 */
export function ChatSettings() {
  return (
    <div className="flex flex-col gap-4">
      <TypingSettings />
      <DefaultTimerSetting />
      <FolderSettings />
      <KeepArchivedSetting />
    </div>
  )
}

function DefaultTimerSetting() {
  const { t } = useTranslation()
  const preferences = useQuery(chatQueries.preferences())
  const change = useChangePreferences()
  return (
    <SettingsSection
      title={t("chat.timer.defaultTitle")}
      description={t("chat.timer.defaultDescription")}
    >
      {preferences.isPending ? (
        <Skeleton className="h-9 w-full" />
      ) : (
        <div className="flex flex-col gap-1 sm:w-64">
          <label htmlFor="default-timer" className="sr-only">
            {t("chat.timer.defaultTitle")}
          </label>
          <DisappearingSelect
            id="default-timer"
            value={preferences.data?.defaultDisappearingSeconds ?? null}
            disabled={change.isPending}
            onChange={(seconds) =>
              change.mutate(
                { defaultDisappearingSeconds: seconds },
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
          />
        </div>
      )}
    </SettingsSection>
  )
}

/** Enviar com Enter, verificação ortográfica, carinhas que viram emoji e som ao enviar: valem para este aparelho. */
function TypingSettings() {
  const { t } = useTranslation()
  const prefs = useChatPrefs()
  const coarse = useMediaQuery("(pointer: coarse)")
  const toggles = [
    {
      key: "enter",
      checked: enterSends(prefs, coarse),
      change: (on: boolean) => setChatPrefs({ enterToSend: on ? "on" : "off" }),
    },
    {
      key: "spellcheck",
      checked: prefs.spellcheck,
      change: (spellcheck: boolean) => setChatPrefs({ spellcheck }),
    },
    {
      key: "emoji",
      checked: prefs.emojiReplace,
      change: (emojiReplace: boolean) => setChatPrefs({ emojiReplace }),
    },
    {
      key: "sound",
      checked: prefs.sendSound,
      change: (sendSound: boolean) => {
        setChatPrefs({ sendSound })
        if (sendSound) {
          playSendSound()
        }
      },
    },
  ] as const
  return (
    <SettingsSection
      title={t("chatPrefs.title")}
      description={t("chatPrefs.description")}
    >
      <ul className="flex flex-col divide-y">
        {toggles.map((toggle) => (
          <li key={toggle.key}>
            <label className="flex min-h-12 items-center justify-between gap-3 py-1 text-sm">
              <span className="flex min-w-0 flex-col">
                {t(`chatPrefs.${toggle.key}`)}
                <span className="text-xs text-muted-foreground">
                  {t(`chatPrefs.${toggle.key}Hint`)}
                </span>
              </span>
              <Switch
                checked={toggle.checked}
                onCheckedChange={(value) => toggle.change(value)}
              />
            </label>
          </li>
        ))}
      </ul>
    </SettingsSection>
  )
}
