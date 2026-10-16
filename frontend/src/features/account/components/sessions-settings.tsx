import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { MonitorSmartphoneIcon } from "lucide-react"

import { QueryState } from "@/components/query-state"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemMedia,
  ItemTitle,
} from "@/components/ui/item"
import { Skeleton } from "@/components/ui/skeleton"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { formatDateTime } from "@/lib/format"
import { accountQueries, useRevokeSession } from "../api"
import { SettingsSection } from "./settings-section"

/** Sessões ativas: dispositivo, data, IP aproximado; encerrar uma ou todas as outras (ID-06). */
export function SessionsSettings() {
  const { t } = useTranslation()
  const sessions = useQuery(accountQueries.sessions())
  const revoke = useRevokeSession()
  const others =
    sessions.data?.filter((session) => !session.current).length ?? 0

  return (
    <SettingsSection
      title={t("settings.sessions")}
      description={t("sessions.description")}
      action={
        others > 0 ? (
          <Button
            variant="outline"
            size="sm"
            className="w-fit"
            onClick={async () => {
              try {
                await revoke.mutateAsync(null)
                toast.add({ title: t("sessions.revoked") })
              } catch (error) {
                toast.add({
                  title: errorMessage(error, t("common.genericError")),
                  type: "error",
                })
              }
            }}
          >
            {t("sessions.revokeOthers")}
          </Button>
        ) : undefined
      }
    >
      <QueryState
        query={sessions}
        loading={
          <Skeleton
            role="status"
            className="h-16 w-full"
            aria-label={t("common.loading")}
          />
        }
      >
        {(list) => (
          <ItemGroup>
            {list.map((session) => (
              <Item key={session.id} variant="outline" size="sm">
                <ItemMedia variant="icon">
                  <MonitorSmartphoneIcon />
                </ItemMedia>
                <ItemContent>
                  <ItemTitle>
                    {session.device}
                    {session.current ? (
                      <Badge variant="secondary">{t("sessions.current")}</Badge>
                    ) : null}
                  </ItemTitle>
                  <ItemDescription>
                    {t("sessions.lastActive", {
                      date: formatDateTime(session.lastAccessedAt),
                      ip: session.approximateIp,
                    })}
                  </ItemDescription>
                </ItemContent>
                {!session.current ? (
                  <ItemActions>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={async () => {
                        try {
                          await revoke.mutateAsync(session.id)
                          toast.add({ title: t("sessions.revoked") })
                        } catch (error) {
                          toast.add({
                            title: errorMessage(
                              error,
                              t("common.genericError")
                            ),
                            type: "error",
                          })
                        }
                      }}
                    >
                      {t("sessions.revoke")}
                    </Button>
                  </ItemActions>
                ) : null}
              </Item>
            ))}
          </ItemGroup>
        )}
      </QueryState>
    </SettingsSection>
  )
}
