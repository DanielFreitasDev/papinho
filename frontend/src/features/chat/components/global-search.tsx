import { Link, useNavigate } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { UserAvatar } from "@/components/user-avatar"
import { Skeleton } from "@/components/ui/skeleton"
import { toast } from "@/components/ui/toast"
import { contactQueries } from "@/features/contact"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { errorMessage } from "@/lib/api-client"
import { formatListTime } from "@/lib/format"
import { useOpenDirect } from "../api"
import { plainText } from "../format/parse"
import { normalize, organizationQueries } from "../organization"
import type { Conversation } from "../types"
import { ConversationAvatar, useConversationLabel } from "./conversation-avatar"

const ROW =
  "flex min-h-14 w-full min-w-0 items-center gap-3 rounded-md px-3 py-2 text-left outline-none hover:bg-muted/50 focus-visible:ring-3 focus-visible:ring-ring/50"

/**
 * Resultados da busca global (GR-06): conversas e contatos pelo nome (no cliente, sem acentos) e mensagens pela
 * busca do servidor (português, sem acentos, trechos de palavra).
 */
export function GlobalSearchResults({
  query,
  meId,
  conversations,
}: {
  query: string
  meId: string
  conversations: Conversation[]
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const openDirect = useOpenDirect()
  const labelOf = useConversationLabel()
  const debounced = useDebouncedValue(query.trim(), 300)
  const messages = useQuery(organizationQueries.search(debounced))
  const contacts = useQuery(contactQueries.list())
  const needle = normalize(query.trim())
  const labels = new Map(conversations.map((c) => [c.id, labelOf(c)]))
  const matchedChats = conversations.filter((conversation) =>
    normalize(
      `${labels.get(conversation.id)} ${conversation.peer?.username ?? ""}`
    ).includes(needle)
  )
  const withChat = new Set(
    conversations.filter((c) => c.kind === "DIRECT").map((c) => c.peer?.userId)
  )
  const matchedContacts = (contacts.data ?? []).filter(
    (contact) =>
      !withChat.has(contact.userId) &&
      normalize(`${contact.label} ${contact.username}`).includes(needle)
  )
  const nothing =
    matchedChats.length === 0 &&
    matchedContacts.length === 0 &&
    (debounced.length < 2 || (messages.data?.length ?? 0) === 0) &&
    !messages.isFetching

  return (
    <div className="flex flex-col gap-3">
      {matchedChats.length > 0 ? (
        <section aria-labelledby="search-chats">
          <h2
            id="search-chats"
            className="px-3 pb-1 text-xs font-semibold text-muted-foreground uppercase"
          >
            {t("search.chats")}
          </h2>
          <ul>
            {matchedChats.map((conversation) => (
              <li key={conversation.id}>
                <Link
                  to="/c/$conversationId"
                  params={{ conversationId: conversation.id }}
                  className={ROW}
                  data-private-scope=""
                >
                  <ConversationAvatar
                    conversation={conversation}
                    className="size-10"
                  />
                  <span
                    data-private="names"
                    className="truncate text-sm font-medium"
                  >
                    {labels.get(conversation.id)}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      {matchedContacts.length > 0 ? (
        <section aria-labelledby="search-contacts">
          <h2
            id="search-contacts"
            className="px-3 pb-1 text-xs font-semibold text-muted-foreground uppercase"
          >
            {t("search.contacts")}
          </h2>
          <ul>
            {matchedContacts.map((contact) => (
              <li key={contact.userId}>
                <button
                  type="button"
                  className={ROW}
                  data-private-scope=""
                  onClick={async () => {
                    try {
                      const conversation = await openDirect.mutateAsync(
                        contact.username
                      )
                      await navigate({
                        to: "/c/$conversationId",
                        params: { conversationId: conversation.id },
                      })
                    } catch (error) {
                      toast.add({
                        title: errorMessage(error, t("common.genericError")),
                        type: "error",
                      })
                    }
                  }}
                >
                  <UserAvatar
                    userId={contact.userId}
                    name={contact.label}
                    hasPhoto={contact.hasPhoto}
                    version={contact.photoVersion}
                    className="size-10"
                  />
                  <span className="flex min-w-0 flex-col">
                    <span
                      data-private="names"
                      className="truncate text-sm font-medium"
                    >
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
        </section>
      ) : null}
      {debounced.length >= 2 ? (
        <section
          aria-labelledby="search-messages"
          aria-busy={messages.isFetching}
        >
          <h2
            id="search-messages"
            className="px-3 pb-1 text-xs font-semibold text-muted-foreground uppercase"
          >
            {t("search.messages")}
          </h2>
          {messages.isPending ? (
            <div
              role="status"
              aria-label={t("common.loading")}
              className="px-3"
            >
              <Skeleton className="h-10 w-full" />
            </div>
          ) : messages.isError ? (
            <p className="px-3 text-sm text-destructive">
              {t("errors.genericDescription")}
            </p>
          ) : (
            <ul>
              {messages.data.map((message) => (
                <li key={message.id}>
                  <Link
                    to="/c/$conversationId"
                    params={{ conversationId: message.conversationId }}
                    search={{ m: message.id }}
                    className={ROW}
                    data-private-scope=""
                  >
                    <span className="flex min-w-0 flex-1 flex-col">
                      <span className="flex items-baseline gap-2">
                        <span
                          data-private="names"
                          className="flex-1 truncate text-sm font-medium"
                        >
                          {labels.get(message.conversationId) ??
                            t("search.otherChat")}
                        </span>
                        <time
                          dateTime={message.createdAt}
                          className="shrink-0 text-xs text-muted-foreground"
                        >
                          {formatListTime(message.createdAt)}
                        </time>
                      </span>
                      <span
                        data-private="previews"
                        className="truncate text-xs text-muted-foreground"
                      >
                        {message.senderId === meId ? `${t("chat.you")}: ` : ""}
                        {plainText(message.body)}
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}
      {nothing ? (
        <p
          role="status"
          className="px-3 py-6 text-center text-sm text-muted-foreground"
        >
          {t("search.noResults")}
        </p>
      ) : null}
    </div>
  )
}
