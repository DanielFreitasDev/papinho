import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { UserXIcon } from "lucide-react"

import { QueryState } from "@/components/query-state"
import { SearchInput } from "@/components/search-input"
import { UserAvatar } from "@/components/user-avatar"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { toast } from "@/components/ui/toast"
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

/** Pessoas bloqueadas, com desbloqueio (CT-03) e, como no WhatsApp, "Adicionar contato bloqueado". */
export function BlockedSettings() {
  const { t } = useTranslation()
  const blocked = useQuery(contactQueries.blocks())
  const block = useBlock()
  const onError = useErrorToast()
  const [adding, setAdding] = useState(false)

  return (
    <SettingsSection
      title={t("settings.blocked")}
      action={
        <Button size="sm" variant="outline" onClick={() => setAdding(true)}>
          <UserXIcon data-icon="inline-start" />
          {t("blocked.add")}
        </Button>
      }
    >
      {adding ? (
        <BlockContactDialog
          blockedIds={new Set((blocked.data ?? []).map((item) => item.userId))}
          onClose={() => setAdding(false)}
        />
      ) : null}
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

/** Escolher um contato para bloquear, com busca; quem já está bloqueado não aparece. */
function BlockContactDialog({
  blockedIds,
  onClose,
}: {
  blockedIds: Set<string>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const contacts = useQuery(contactQueries.list())
  const block = useBlock()
  const onError = useErrorToast()
  const [query, setQuery] = useState("")
  const term = query.trim().toLowerCase()
  const shown = (contacts.data ?? []).filter(
    (contact) =>
      !blockedIds.has(contact.userId) &&
      `${contact.label} ${contact.username}`.toLowerCase().includes(term)
  )
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("blocked.add")}</DialogTitle>
          <DialogDescription>{t("blocked.addDescription")}</DialogDescription>
        </DialogHeader>
        <SearchInput
          value={query}
          aria-label={t("blocked.search")}
          placeholder={t("blocked.search")}
          onValueChange={setQuery}
        />
        <ul className="-mx-2 flex max-h-72 flex-col overflow-y-auto">
          {shown.length === 0 ? (
            <li className="px-2 py-4 text-sm text-muted-foreground">
              {t("blocked.noContacts")}
            </li>
          ) : null}
          {shown.map((contact) => (
            <li key={contact.userId}>
              <button
                type="button"
                data-ripple=""
                disabled={block.isPending}
                onClick={() =>
                  block.mutate(
                    { userId: contact.userId, blocked: true },
                    {
                      onSuccess: () => {
                        toast.add({
                          title: t("blocked.done", { name: contact.label }),
                        })
                        onClose()
                      },
                      onError,
                    }
                  )
                }
                className="flex min-h-12 w-full items-center gap-3 rounded-lg px-2 py-1.5 text-left text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50 [@media(hover:hover)]:hover:bg-muted"
              >
                <UserAvatar
                  privateItem
                  userId={contact.userId}
                  name={contact.label}
                  hasPhoto={contact.hasPhoto}
                  version={contact.photoVersion}
                  className="size-9"
                />
                <span className="flex min-w-0 flex-col">
                  <span className="truncate font-medium" data-private="names">
                    {contact.label}
                  </span>
                  <span className="truncate text-xs text-muted-foreground">
                    @{contact.username}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  )
}
