import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { UserAvatar } from "@/components/user-avatar"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { contactQueries } from "@/features/contact"

/** Lista de contatos com caixa de seleção e busca, para criar grupo ou adicionar membros (GR-01). */
export function ContactPicker({
  selected,
  onChange,
  exclude = [],
}: {
  selected: string[]
  onChange: (ids: string[]) => void
  /** Quem já está no grupo. */
  exclude?: string[]
}) {
  const { t } = useTranslation()
  const contacts = useQuery(contactQueries.list())
  const [filter, setFilter] = useState("")
  const term = filter.trim().toLocaleLowerCase()
  const visible = (contacts.data ?? [])
    .filter((contact) => !exclude.includes(contact.userId))
    .filter(
      (contact) =>
        term === "" ||
        contact.label.toLocaleLowerCase().includes(term) ||
        contact.username.includes(term)
    )

  return (
    <div className="flex min-h-0 flex-col gap-2">
      <Input
        type="search"
        aria-label={t("groups.searchContacts")}
        placeholder={t("groups.searchContacts")}
        value={filter}
        onChange={(event) => setFilter(event.target.value)}
      />
      <fieldset className="flex max-h-64 flex-col gap-1 overflow-y-auto">
        <legend className="sr-only">{t("groups.chooseMembers")}</legend>
        {contacts.isPending ? (
          <Skeleton className="h-10 w-full" />
        ) : visible.length === 0 ? (
          <p className="px-2 py-3 text-sm text-muted-foreground">
            {t("groups.noContacts")}
          </p>
        ) : (
          visible.map((contact) => (
            <label
              key={contact.userId}
              className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md px-2 py-1 hover:bg-muted"
            >
              <input
                type="checkbox"
                className="size-5 accent-primary"
                checked={selected.includes(contact.userId)}
                onChange={() =>
                  onChange(
                    selected.includes(contact.userId)
                      ? selected.filter((id) => id !== contact.userId)
                      : [...selected, contact.userId]
                  )
                }
              />
              <UserAvatar
                userId={contact.userId}
                name={contact.label}
                hasPhoto={contact.hasPhoto}
                version={contact.photoVersion}
                className="size-8"
              />
              <span className="flex min-w-0 flex-col">
                <span className="truncate text-sm">{contact.label}</span>
                <span className="truncate text-xs text-muted-foreground">
                  @{contact.username}
                </span>
              </span>
            </label>
          ))
        )}
      </fieldset>
    </div>
  )
}
