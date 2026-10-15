import { useEffect } from "react"
import { Link, useParams } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  EllipsisVerticalIcon,
  InboxIcon,
  MailIcon,
  MessagesSquareIcon,
} from "lucide-react"
import { cn } from "cn"

import { UserAvatar } from "@/components/user-avatar"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Item, ItemActions, ItemGroup } from "@/components/ui/item"
import { Skeleton } from "@/components/ui/skeleton"
import { formatListTime } from "@/lib/format"
import { chatQueries, markDelivered, useConversationAction } from "../api"
import { plainText } from "../format/parse"
import { useActivity } from "../activity"
import type { Conversation } from "../types"

/** Lista de conversas (ou de solicitações) com prévia, horário e não lidas (MS-14). */
export function ConversationList({
  meId,
  requests = false,
}: {
  meId: string
  requests?: boolean
}) {
  const { t } = useTranslation()
  const conversations = useQuery(chatQueries.list(requests))
  const pending = useQuery({ ...chatQueries.list(true), enabled: !requests })

  // Abrir o app confirma a entrega do que chegou enquanto estava fora (MS-02).
  useEffect(() => {
    conversations.data?.forEach((conversation) => {
      if (
        conversation.lastMessage &&
        conversation.lastMessage.senderId !== meId
      ) {
        markDelivered(conversation.id, conversation.lastSeq)
      }
    })
  }, [conversations.data, meId])

  if (conversations.isPending) {
    return (
      <div
        role="status"
        aria-label={t("common.loading")}
        className="flex flex-col gap-3 p-3"
      >
        {[0, 1, 2].map((index) => (
          <div key={index} className="flex items-center gap-3">
            <Skeleton className="size-10 rounded-full" />
            <Skeleton className="h-4 flex-1" />
          </div>
        ))}
      </div>
    )
  }
  if (conversations.isError) {
    return (
      <Alert variant="destructive" className="m-3">
        <AlertDescription>{t("errors.genericDescription")}</AlertDescription>
      </Alert>
    )
  }
  const requestCount = pending.data?.length ?? 0
  return (
    <div className="flex flex-col gap-2">
      {!requests && requestCount > 0 ? (
        <Link
          to="/requests"
          className="flex min-h-11 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <InboxIcon aria-hidden="true" className="size-5" />
          <span className="flex-1">
            {t("chat.requestsCount", { count: requestCount })}
          </span>
        </Link>
      ) : null}
      {conversations.data.length === 0 ? (
        <Empty className="py-10">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              {requests ? <InboxIcon /> : <MessagesSquareIcon />}
            </EmptyMedia>
            <EmptyTitle>
              {requests ? t("chat.requestsEmpty") : t("chat.emptyList")}
            </EmptyTitle>
            <EmptyDescription>
              {requests
                ? t("chat.requestsEmptyDescription")
                : t("chat.emptyListDescription")}
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <ItemGroup
          aria-label={requests ? t("chat.requests") : t("chat.conversations")}
          className="gap-0.5!"
        >
          {conversations.data.map((conversation) => (
            <ConversationItem
              key={conversation.id}
              conversation={conversation}
              meId={meId}
            />
          ))}
        </ItemGroup>
      )}
    </div>
  )
}

function ConversationItem({
  conversation,
  meId,
}: {
  conversation: Conversation
  meId: string
}) {
  const { t } = useTranslation()
  const params = useParams({ strict: false })
  const activity = useActivity(conversation.id)
  const action = useConversationAction()
  const active = params.conversationId === conversation.id
  const unread = conversation.unreadCount > 0 || conversation.markedUnread
  const last = conversation.lastMessage
  const preview = activity
    ? t(activity.activity === "recording" ? "chat.recording" : "chat.typing")
    : last
      ? `${last.senderId === meId ? `${t("chat.you")}: ` : ""}${
          last.attachmentKind
            ? `${t(`media.kind.${last.attachmentKind}`)}${last.body ? ` · ${plainText(last.body)}` : ""}`
            : plainText(last.body)
        }`
      : ""

  return (
    <Item
      size="sm"
      className={cn(
        "relative gap-0 p-0 has-[a:hover]:bg-muted/50",
        active && "bg-muted/50"
      )}
    >
      <Link
        to="/c/$conversationId"
        params={{ conversationId: conversation.id }}
        aria-current={active ? "page" : undefined}
        className="flex min-h-14 min-w-0 flex-1 items-center gap-3 rounded-md px-3 py-2 outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        <UserAvatar
          userId={conversation.peer.userId}
          name={conversation.peer.label}
          hasPhoto={conversation.peer.hasPhoto}
          version={conversation.peer.photoVersion}
          className="size-10"
        />
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="flex items-baseline gap-2">
            <span
              className={cn(
                "flex-1 truncate text-sm",
                unread ? "font-semibold" : "font-medium"
              )}
            >
              {conversation.peer.label}
            </span>
            {last ? (
              <time
                dateTime={last.createdAt}
                className={cn(
                  "shrink-0 text-xs",
                  unread
                    ? "font-medium text-primary"
                    : active
                      ? "text-foreground/80"
                      : "text-muted-foreground"
                )}
              >
                {formatListTime(last.createdAt)}
              </time>
            ) : null}
          </span>
          <span className="flex items-center gap-2">
            <span
              className={cn(
                "flex-1 truncate text-xs",
                activity
                  ? "text-primary"
                  : active
                    ? "text-foreground/80"
                    : "text-muted-foreground"
              )}
            >
              {preview}
            </span>
            {conversation.unreadCount > 0 ? (
              <Badge className="min-w-5 justify-center">
                <span aria-hidden="true">{conversation.unreadCount}</span>
                <span className="sr-only">
                  {t("chat.unreadCount", { count: conversation.unreadCount })}
                </span>
              </Badge>
            ) : conversation.markedUnread ? (
              <Badge className="size-3 p-0">
                <span className="sr-only">{t("chat.markedUnread")}</span>
              </Badge>
            ) : null}
          </span>
        </span>
      </Link>
      {conversation.state === "ACTIVE" ? (
        <ItemActions className="pr-1">
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={t("chat.conversationActions", {
                    name: conversation.peer.label,
                  })}
                />
              }
            >
              <EllipsisVerticalIcon />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                disabled={unread}
                onClick={() =>
                  action.mutate({
                    conversationId: conversation.id,
                    action: "unread",
                  })
                }
              >
                <MailIcon />
                {t("chat.markUnread")}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </ItemActions>
      ) : null}
    </Item>
  )
}
