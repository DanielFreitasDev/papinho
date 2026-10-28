import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { MessageCircleIcon, UserPlusIcon, UserSearchIcon } from "lucide-react"

import { QueryError } from "@/components/query-state"
import { SearchInput } from "@/components/search-input"
import { UserAvatar } from "@/components/user-avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field"
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
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { errorMessage } from "@/lib/api-client"
import { contactQueries, useAddContact } from "../api"
import type { Person } from "../types"

/** Letras mínimas para buscar (o servidor devolve vazio com menos). */
const MIN_QUERY = 2

/**
 * Adicionar contato (CT-01): enquanto se digita, aparecem as pessoas da instância pelo nome ou pelo @usuario, como
 * na busca de perfis do Facebook e do Instagram (pedido do dono de 2026-10-28). Daqui dá para adicionar e, já
 * contato, abrir a conversa. Quem desligou "Aparecer na busca por nome" só aparece pelo @usuario exato.
 */
export function AddContactDialog({
  open,
  onOpenChange,
  onStartChat,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Abre a conversa com a pessoa (a lista de contatos faz o mesmo). */
  onStartChat: (username: string) => Promise<void>
}) {
  const { t } = useTranslation()
  const [query, setQuery] = useState("")
  const term = useDebouncedValue(query.trim().replace(/^@+/, "").trim(), 250)
  const searching = term.length >= MIN_QUERY
  const results = useQuery({
    ...contactQueries.search(term),
    enabled: open && searching,
  })
  const add = useAddContact()
  const [adding, setAdding] = useState<string | null>(null)
  const [opening, setOpening] = useState<string | null>(null)

  async function addPerson(person: Person) {
    setAdding(person.userId)
    try {
      await add.mutateAsync({ username: person.username })
      toast.add({
        title: t("contacts.added"),
        description: t("contacts.addedName", { name: person.displayName }),
        type: "success",
      })
    } catch (error) {
      toast.add({
        title: errorMessage(error, t("common.genericError")),
        type: "error",
      })
    } finally {
      setAdding(null)
    }
  }

  async function startChat(person: Person) {
    setOpening(person.userId)
    try {
      await onStartChat(person.username)
      close(false)
    } finally {
      setOpening(null)
    }
  }

  function close(next: boolean) {
    if (!next) {
      setQuery("")
    }
    onOpenChange(next)
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] flex-col">
        <DialogHeader>
          <DialogTitle>{t("contacts.addTitle")}</DialogTitle>
          <DialogDescription>{t("contacts.addDescription")}</DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="add-contact-search">
            {t("contacts.searchLabel")}
          </FieldLabel>
          <SearchInput
            id="add-contact-search"
            value={query}
            onValueChange={setQuery}
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            enterKeyHint="search"
            maxLength={64}
            aria-describedby="add-contact-hint"
          />
          <FieldDescription id="add-contact-hint">
            {t("contacts.searchHint")}
          </FieldDescription>
        </Field>
        <section
          aria-label={t("contacts.searchResults")}
          aria-busy={searching && results.isFetching}
          className="-mx-2 min-h-0 flex-1 overflow-y-auto px-2"
        >
          {!searching ? null : results.isPending ? (
            <div role="status" aria-label={t("common.loading")}>
              {[0, 1, 2].map((index) => (
                <Skeleton key={index} className="my-2 h-12 w-full" />
              ))}
            </div>
          ) : results.isError ? (
            <QueryError
              error={results.error}
              retrying={results.isFetching}
              onRetry={() => void results.refetch()}
            />
          ) : results.data.length === 0 ? (
            <Empty className="py-6">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <UserSearchIcon />
                </EmptyMedia>
                <EmptyTitle>{t("contacts.nobodyFound")}</EmptyTitle>
                <EmptyDescription>
                  {t("contacts.nobodyFoundDescription")}
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <ItemGroup>
              {results.data.map((person) => (
                <Item key={person.userId} size="sm" className="px-0">
                  <ItemMedia>
                    <UserAvatar
                      privateItem
                      userId={person.userId}
                      name={person.displayName}
                      hasPhoto={person.hasPhoto}
                      version={person.photoVersion}
                    />
                  </ItemMedia>
                  <ItemContent className="min-w-0">
                    <ItemTitle className="truncate" data-private="names">
                      {person.displayName}
                    </ItemTitle>
                    <ItemDescription className="truncate">
                      @{person.username}
                    </ItemDescription>
                  </ItemContent>
                  <ItemActions>
                    {person.contact ? (
                      <>
                        <Badge
                          variant="secondary"
                          className="hidden sm:inline-flex"
                        >
                          {t("contacts.isContact")}
                        </Badge>
                        <Button
                          size="sm"
                          variant="outline"
                          aria-label={`${t("contacts.startConversation")} ${person.displayName}`}
                          disabled={opening !== null}
                          onClick={() => void startChat(person)}
                        >
                          {opening === person.userId ? (
                            <Spinner data-icon="inline-start" />
                          ) : (
                            <MessageCircleIcon data-icon="inline-start" />
                          )}
                          {t("contacts.chat")}
                        </Button>
                      </>
                    ) : (
                      <Button
                        size="sm"
                        aria-label={`${t("contacts.add")} ${person.displayName}`}
                        disabled={adding !== null}
                        onClick={() => void addPerson(person)}
                      >
                        {adding === person.userId ? (
                          <Spinner data-icon="inline-start" />
                        ) : (
                          <UserPlusIcon data-icon="inline-start" />
                        )}
                        {t("contacts.add")}
                      </Button>
                    )}
                  </ItemActions>
                </Item>
              ))}
            </ItemGroup>
          )}
        </section>
      </DialogContent>
    </Dialog>
  )
}
