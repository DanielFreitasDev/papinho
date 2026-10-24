import { Link } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { StarIcon } from "lucide-react"

import { QueryState } from "@/components/query-state"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import {
  Item,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemTitle,
} from "@/components/ui/item"
import { Skeleton } from "@/components/ui/skeleton"
import { formatDateTime } from "@/lib/format"
import { chatQueries } from "../api"
import { useConversationLabel } from "./conversation-avatar"
import { plainText } from "../format/parse"

/** Mensagens favoritas de todas as conversas (MS-04). */
export function StarredMessages() {
  const { t } = useTranslation()
  const labelOf = useConversationLabel()
  const starred = useQuery(chatQueries.starred())
  const conversations = useQuery(chatQueries.list(false))
  if (starred.isPending) {
    return (
      <Skeleton
        className="mx-(--spacing-gutter) my-4 h-24"
        role="status"
        aria-label={t("common.loading")}
      />
    )
  }
  if (starred.isError) {
    return <QueryState query={starred}>{() => null}</QueryState>
  }
  if (!starred.data || starred.data.length === 0) {
    return (
      <Empty className="py-10">
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <StarIcon />
          </EmptyMedia>
          <EmptyTitle>{t("chat.starredEmpty")}</EmptyTitle>
          <EmptyDescription>
            {t("chat.starredEmptyDescription")}
          </EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }
  const labels = new Map(
    conversations.data?.map((conversation) => [
      conversation.id,
      labelOf(conversation),
    ])
  )
  return (
    <ItemGroup
      className="px-(--spacing-gutter) py-4"
      aria-label={t("chat.starredMessages")}
    >
      {starred.data.map((message) => (
        <Item
          key={message.id}
          variant="outline"
          render={
            <Link
              to="/c/$conversationId"
              params={{ conversationId: message.conversationId }}
            />
          }
        >
          <ItemContent className="min-w-0">
            <ItemTitle className="truncate">
              {labels.get(message.conversationId) ?? ""}
            </ItemTitle>
            <ItemDescription className="line-clamp-2">
              {plainText(message.body)}
            </ItemDescription>
            <time
              dateTime={message.createdAt}
              className="text-xs text-muted-foreground"
            >
              {formatDateTime(message.createdAt)}
            </time>
          </ItemContent>
        </Item>
      ))}
    </ItemGroup>
  )
}
