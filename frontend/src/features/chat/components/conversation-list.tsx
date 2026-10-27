import { useEffect, useRef, useState } from "react"
import { Link, useParams } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  ArchiveIcon,
  BellOffIcon,
  InboxIcon,
  LockKeyholeIcon,
  MessagesSquareIcon,
  PencilIcon,
  PinIcon,
  PlusIcon,
  Trash2Icon,
} from "lucide-react"
import { cn } from "cn"

import { ConfirmDialog } from "@/components/confirm-dialog"
import { SearchInput } from "@/components/search-input"
import { toast } from "@/components/ui/toast"
import { useErrorToast } from "@/hooks/use-error-toast"
import { QueryError } from "@/components/query-state"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Item, ItemActions, ItemGroup } from "@/components/ui/item"
import { useFlip } from "@/hooks/use-flip"
import { Skeleton } from "@/components/ui/skeleton"
import { formatListTime } from "@/lib/format"
import { privacyQueries } from "@/features/privacy"
import { chatQueries, markDelivered } from "../api"
import {
  applyListFilter,
  isMuted,
  organizationQueries,
  useDeleteFolder,
  type ListFilter,
} from "../organization"
import { plainText } from "../format/parse"
import { useActivity } from "../activity"
import { describeNotice } from "../system-notice"
import type { Conversation, Folder } from "../types"
import { ActivityText } from "./activity-text"
import { ConversationAvatar, useConversationLabel } from "./conversation-avatar"
import { useContextMenu } from "@/hooks/use-context-menu"
import { ConversationMenu } from "./conversation-menu"
import { GlobalSearchResults } from "./global-search"
import { FolderDialog } from "./folder-settings"

/**
 * Lista de conversas (ou de solicitações, ou a pasta das trancadas) com prévia, horário e não lidas (MS-14).
 */
export function ConversationList({
  meId,
  requests = false,
  locked = false,
}: {
  meId: string
  requests?: boolean
  /** Pasta das conversas trancadas (PV-03), já aberta com o código. */
  locked?: boolean
}) {
  const { t } = useTranslation()
  const list = useQuery({ ...chatQueries.list(requests), enabled: !locked })
  const lockedList = useQuery({ ...chatQueries.locked(), enabled: locked })
  const conversations = locked ? lockedList : list
  const main = !requests && !locked
  const pending = useQuery({ ...chatQueries.list(true), enabled: main })
  const lockedCount = useQuery({
    ...privacyQueries.lockedCount(),
    enabled: main,
  })
  const folders = useQuery({ ...organizationQueries.folders(), enabled: main })
  const [filter, setFilter] = useState<ListFilter>({ kind: "all" })
  // Lista nova (+ na barra) ou a aberta, para editar; e a que vai ser apagada.
  const [folderDialog, setFolderDialog] = useState<Folder | "new" | null>(null)
  const [deletingFolder, setDeletingFolder] = useState<Folder | null>(null)
  const removeFolder = useDeleteFolder()
  const onError = useErrorToast()
  const [query, setQuery] = useState("")
  // Conversa que sobe com mensagem nova, fixada, arquivada: as outras deslizam até o lugar novo.
  const group = useRef<HTMLDivElement>(null)
  useFlip(group)

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
      <QueryError
        error={conversations.error}
        retrying={conversations.isFetching}
        onRetry={() => void conversations.refetch()}
        className="px-3"
      />
    )
  }
  const requestCount = pending.data?.length ?? 0
  const lockedTotal = lockedCount.data?.count ?? 0
  const searching = main && query.trim().length > 0
  const shown = main
    ? applyListFilter(conversations.data, filter)
    : conversations.data
  const archivedCount = main
    ? conversations.data.filter((c) => c.organization.archived).length
    : 0
  const filters: { key: string; label: string; value: ListFilter }[] = [
    { key: "all", label: t("organize.filter.all"), value: { kind: "all" } },
    {
      key: "unread",
      label: t("organize.filter.unread"),
      value: { kind: "unread" },
    },
    {
      key: "favorites",
      label: t("organize.filter.favorites"),
      value: { kind: "favorites" },
    },
    {
      key: "groups",
      label: t("organize.filter.groups"),
      value: { kind: "groups" },
    },
    ...(folders.data ?? []).map((folder) => ({
      key: folder.id,
      label: folder.name,
      value: { kind: "folder", folderId: folder.id } as ListFilter,
    })),
  ]
  const filterKey = filter.kind === "folder" ? filter.folderId : filter.kind
  // Quantas conversas com mensagem não lida cada filtro tem, como no WhatsApp ("Não lidas 4", "Grupos 2").
  const unreadIn = (value: ListFilter) =>
    value.kind === "all" || value.kind === "archived"
      ? 0
      : applyListFilter(conversations.data, value).filter(
          (conversation) =>
            conversation.unreadCount > 0 || conversation.markedUnread
        ).length
  const openFolder =
    filter.kind === "folder"
      ? folders.data?.find((folder) => folder.id === filter.folderId)
      : undefined
  if (searching) {
    return (
      <div className="flex flex-col gap-2">
        <SearchBox query={query} onChange={setQuery} />
        <GlobalSearchResults
          query={query}
          meId={meId}
          conversations={conversations.data}
        />
      </div>
    )
  }
  return (
    <div className="flex flex-col gap-2">
      {main ? (
        <>
          <SearchBox query={query} onChange={setQuery} />
          {/* Filtros e listas do usuário rolam de lado de propósito quando não cabem (data-scroll-x, AR-02). */}
          <div
            role="group"
            aria-label={t("organize.filter.label")}
            className="flex scroll-fade-x gap-1.5 overflow-x-auto px-2 pb-1"
            data-scroll-x=""
          >
            {filters.map((item) => {
              const count = unreadIn(item.value)
              return (
                <Button
                  key={item.key}
                  size="sm"
                  variant={filterKey === item.key ? "default" : "outline"}
                  aria-pressed={filterKey === item.key}
                  className="shrink-0 rounded-full"
                  onClick={() => setFilter(item.value)}
                >
                  {item.label}
                  {count > 0 ? (
                    <span
                      key={count}
                      className="animate-pop text-xs tabular-nums opacity-80"
                    >
                      <span aria-hidden="true">{count}</span>
                      <span className="sr-only">
                        {t("organize.filter.unreadIn", { count })}
                      </span>
                    </span>
                  ) : null}
                </Button>
              )
            })}
            {/* Nova lista direto da barra, como o "+" do WhatsApp. */}
            <Button
              size="icon-sm"
              variant="outline"
              className="shrink-0 rounded-full"
              aria-label={t("organize.createList")}
              onClick={() => setFolderDialog("new")}
            >
              <PlusIcon />
            </Button>
          </div>
        </>
      ) : null}
      {main && openFolder ? (
        <div className="flex animate-in items-center gap-1 px-2 duration-(--duration-base) ease-(--ease-enter) fade-in slide-in-from-top-1">
          <h2
            className="min-w-0 flex-1 truncate text-sm font-semibold"
            data-private="names"
          >
            {openFolder.name}
          </h2>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setFolderDialog(openFolder)}
          >
            <PencilIcon data-icon="inline-start" />
            {t("organize.editList")}
          </Button>
          <Button
            size="icon-sm"
            variant="ghost"
            aria-label={t("organize.deleteFolder", { name: openFolder.name })}
            onClick={() => setDeletingFolder(openFolder)}
          >
            <Trash2Icon />
          </Button>
        </div>
      ) : null}
      {folderDialog ? (
        <FolderDialog
          folder={folderDialog === "new" ? null : folderDialog}
          onClose={() => setFolderDialog(null)}
          onSaved={(saved) => setFilter({ kind: "folder", folderId: saved.id })}
        />
      ) : null}
      <ConfirmDialog
        open={deletingFolder !== null}
        onOpenChange={(open) => (open ? undefined : setDeletingFolder(null))}
        title={t("organize.deleteFolderTitle", {
          name: deletingFolder?.name ?? "",
        })}
        description={t("organize.deleteFolderDescription")}
        confirmLabel={t("common.delete")}
        onConfirm={() =>
          deletingFolder &&
          removeFolder.mutate(deletingFolder.id, {
            onSuccess: () => {
              setFilter({ kind: "all" })
              toast.add({ title: t("organize.folderDeleted") })
            },
            onError,
          })
        }
      />
      {main && filter.kind === "all" && archivedCount > 0 ? (
        <button
          type="button"
          onClick={() => setFilter({ kind: "archived" })}
          data-ripple=""
          className="flex min-h-11 items-center gap-3 rounded-lg px-3 py-2 text-left text-sm font-medium outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <ArchiveIcon aria-hidden="true" className="size-5" />
          <span className="flex-1">{t("organize.archivedFolder")}</span>
          <span className="text-xs text-muted-foreground">{archivedCount}</span>
        </button>
      ) : null}
      {main && filter.kind === "archived" ? (
        <div className="flex items-center gap-2 px-2">
          <h2 className="flex-1 text-sm font-semibold">
            {t("organize.archivedFolder")}
          </h2>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setFilter({ kind: "all" })}
          >
            {t("common.back")}
          </Button>
        </div>
      ) : null}
      {main && filter.kind === "all" && lockedTotal > 0 ? (
        <Link
          to="/locked"
          data-ripple=""
          className="flex min-h-11 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <LockKeyholeIcon aria-hidden="true" className="size-5" />
          <span className="flex-1">{t("privacy.locked.folder")}</span>
        </Link>
      ) : null}
      {main && filter.kind === "all" && requestCount > 0 ? (
        <Link
          to="/requests"
          data-ripple=""
          className="flex min-h-11 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <InboxIcon aria-hidden="true" className="size-5" />
          <span className="flex-1">
            {t("chat.requestsCount", { count: requestCount })}
          </span>
        </Link>
      ) : null}
      {main && filter.kind !== "all" && shown.length === 0 ? (
        <p className="px-3 py-6 text-center text-sm text-muted-foreground">
          {t("organize.filter.empty")}
        </p>
      ) : shown.length === 0 ? (
        <Empty className="py-10">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              {requests ? (
                <InboxIcon />
              ) : locked ? (
                <LockKeyholeIcon />
              ) : (
                <MessagesSquareIcon />
              )}
            </EmptyMedia>
            <EmptyTitle>
              {requests
                ? t("chat.requestsEmpty")
                : locked
                  ? t("privacy.locked.empty")
                  : t("chat.emptyList")}
            </EmptyTitle>
            <EmptyDescription>
              {requests
                ? t("chat.requestsEmptyDescription")
                : locked
                  ? t("privacy.locked.emptyDescription")
                  : t("chat.emptyListDescription")}
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <ItemGroup
          ref={group}
          aria-label={
            requests
              ? t("chat.requests")
              : locked
                ? t("privacy.locked.folder")
                : t("chat.conversations")
          }
          className="gap-0.5!"
        >
          {shown.map((conversation) => (
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
  // A contagem de não lidas que muda depois de a linha aparecer chega com o "pop" (a de quando montou, não).
  const [initialUnread] = useState(conversation.unreadCount)
  const labelOf = useConversationLabel()
  const label = labelOf(conversation)
  const active = params.conversationId === conversation.id
  const unread = conversation.unreadCount > 0 || conversation.markedUnread
  // Botão direito na linha abre o menu da conversa no ponto do clique, como no WhatsApp Web.
  const menu = useContextMenu()
  const last = conversation.lastMessage
  const sender =
    last && last.senderId === meId
      ? `${t("chat.you")}: `
      : last?.senderLabel
        ? `${last.senderLabel}: `
        : ""
  const preview = activity
    ? t(activity.activity === "recording" ? "chat.recording" : "chat.typing")
    : conversation.draft && !active
      ? t("chat.draftPreview", { text: plainText(conversation.draft) })
      : last?.kind === "SYSTEM"
        ? describeNotice(last.body, meId, t)
        : last?.kind === "POLL" && !last.deleted
          ? `${sender}📊 ${plainText(last.body)}`
          : last?.sticker && !last.deleted
            ? `${sender}${t("sticker.label")}`
            : last?.deleted
              ? last.senderId === meId
                ? t("chat.deletedByMe")
                : t("chat.deleted")
              : last
                ? `${sender}${
                    last.attachmentKind
                      ? `${t(`media.kind.${last.attachmentKind}`)}${last.body ? ` · ${plainText(last.body)}` : ""}`
                      : plainText(last.body)
                  }`
                : ""

  return (
    <Item
      size="sm"
      data-flip-key={conversation.id}
      onContextMenu={
        conversation.state === "ACTIVE" ? menu.onContextMenu : undefined
      }
      className={cn(
        "relative gap-0 p-0 has-[a:hover]:bg-muted/50",
        active && "bg-muted/50"
      )}
    >
      <Link
        to="/c/$conversationId"
        params={{ conversationId: conversation.id }}
        aria-current={active ? "page" : undefined}
        data-private-scope=""
        data-ripple=""
        className="flex min-h-14 min-w-0 flex-1 items-center gap-3 rounded-md px-3 py-2 outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        <ConversationAvatar conversation={conversation} className="size-10" />
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="flex items-baseline gap-2">
            <span
              data-private="names"
              className={cn(
                "flex-1 truncate text-sm",
                unread ? "font-semibold" : "font-medium"
              )}
            >
              {label}
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
              data-private={activity ? undefined : "previews"}
              className={cn(
                "flex-1 truncate text-xs",
                activity
                  ? "text-primary"
                  : active
                    ? "text-foreground/80"
                    : "text-muted-foreground"
              )}
            >
              {activity ? <ActivityText text={preview} /> : preview}
            </span>
            {isMuted(conversation) ? (
              <BellOffIcon
                role="img"
                aria-label={t("organize.mutedLabel")}
                className="size-4 shrink-0 text-muted-foreground"
              />
            ) : null}
            {conversation.organization.pinnedAt ? (
              <PinIcon
                role="img"
                aria-label={t("organize.pinnedLabel")}
                className="size-4 shrink-0 text-muted-foreground"
              />
            ) : null}
            {conversation.unreadCount > 0 ? (
              <Badge
                key={conversation.unreadCount}
                className={cn(
                  "min-w-5 justify-center",
                  conversation.unreadCount !== initialUnread && "animate-pop"
                )}
              >
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
          <ConversationMenu
            conversation={conversation}
            label={label}
            menu={menu}
          />
        </ItemActions>
      ) : null}
    </Item>
  )
}

/** Campo da busca global (GR-06): conversas, contatos e mensagens. */
function SearchBox({
  query,
  onChange,
}: {
  query: string
  onChange: (query: string) => void
}) {
  const { t } = useTranslation()
  return (
    <div role="search" className="px-2">
      <SearchInput
        value={query}
        aria-label={t("search.global")}
        placeholder={t("search.globalPlaceholder")}
        onValueChange={onChange}
        onKeyDown={(event) => {
          if (event.key === "Escape" && query) {
            event.preventDefault()
            onChange("")
          }
        }}
      />
    </div>
  )
}
