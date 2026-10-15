import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

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

/** Pessoas bloqueadas, com desbloqueio (CT-03). */
export function BlockedSettings() {
  const { t } = useTranslation()
  const blocked = useQuery(contactQueries.blocks())
  const block = useBlock()

  return (
    <SettingsSection title={t("settings.blocked")}>
      {blocked.data && blocked.data.length > 0 ? (
        <ItemGroup>
          {blocked.data.map((person) => (
            <Item key={person.userId} variant="outline" size="sm">
              <ItemContent className="min-w-0">
                <ItemTitle className="truncate">{person.displayName}</ItemTitle>
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
                    block.mutate({ userId: person.userId, blocked: false })
                  }
                >
                  {t("contacts.unblock")}
                </Button>
              </ItemActions>
            </Item>
          ))}
        </ItemGroup>
      ) : (
        <p className="text-sm text-muted-foreground">{t("blocked.empty")}</p>
      )}
    </SettingsSection>
  )
}
