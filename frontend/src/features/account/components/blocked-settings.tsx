import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { QueryState } from "@/components/query-state"
import { Button } from "@/components/ui/button"
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemTitle,
} from "@/components/ui/item"
import { contactQueries, useBlock } from "@/features/contact"
import { formatDate } from "@/lib/format"
import { SettingsSection } from "./settings-section"
import { useErrorToast } from "@/hooks/use-error-toast"

/** Pessoas bloqueadas, com desbloqueio (CT-03). */
export function BlockedSettings() {
  const { t } = useTranslation()
  const blocked = useQuery(contactQueries.blocks())
  const block = useBlock()
  const onError = useErrorToast()

  return (
    <SettingsSection title={t("settings.blocked")}>
      <QueryState
        query={blocked}
        isEmpty={(list) => list.length === 0}
        empty={{ title: t("blocked.empty") }}
      >
        {(list) => (
          <ItemGroup>
            {list.map((person) => (
              <Item key={person.userId} variant="outline" size="sm">
                <ItemContent className="min-w-0">
                  <ItemTitle className="truncate">
                    {person.displayName}
                  </ItemTitle>
                  <ItemDescription>
                    @{person.username} ·{" "}
                    {t("blocked.since", { date: formatDate(person.blockedAt) })}
                  </ItemDescription>
                </ItemContent>
                <ItemActions>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      block.mutate(
                        { userId: person.userId, blocked: false },
                        { onError }
                      )
                    }
                  >
                    {t("contacts.unblock")}
                  </Button>
                </ItemActions>
              </Item>
            ))}
          </ItemGroup>
        )}
      </QueryState>
    </SettingsSection>
  )
}
