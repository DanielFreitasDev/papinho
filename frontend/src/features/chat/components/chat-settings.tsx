import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { Skeleton } from "@/components/ui/skeleton"
import { toast } from "@/components/ui/toast"
import { SettingsSection } from "@/features/account"
import { errorMessage } from "@/lib/api-client"
import { chatQueries, useChangePreferences } from "../api"
import { DisappearingSelect } from "./disappearing-select"

/** Configurações > Conversas: timer padrão de mensagens temporárias para conversas novas (MS-07). */
export function ChatSettings() {
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
              change.mutate(seconds, {
                onSuccess: () => toast.add({ title: t("common.saved") }),
                onError: (error) =>
                  toast.add({
                    title: errorMessage(error, t("common.genericError")),
                    type: "error",
                  }),
              })
            }
          />
        </div>
      )}
    </SettingsSection>
  )
}
