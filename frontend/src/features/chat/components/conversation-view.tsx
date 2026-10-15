import { Fragment, useEffect, useMemo, useRef, useState } from "react"
import { Link, useNavigate } from "@tanstack/react-router"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  ArrowLeftIcon,
  ForwardIcon,
  MessageCircleIcon,
  PinIcon,
  StarIcon,
  XIcon,
} from "lucide-react"

import { UserAvatar } from "@/components/user-avatar"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button, buttonVariants } from "@/components/ui/button"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Marker, MarkerContent } from "@/components/ui/marker"
import {
  MessageScroller,
  MessageScrollerButton,
  MessageScrollerContent,
  MessageScrollerItem,
  MessageScrollerProvider,
  MessageScrollerViewport,
  useMessageScroller,
} from "@/components/ui/message-scroller"
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { toast } from "@/components/ui/toast"
import { useBlock } from "@/features/contact"
import { useMediaQuery } from "@/hooks/use-media-query"
import { MAX_FILE_SIZE, type Track } from "@/features/media"
import { ApiError, errorMessage } from "@/lib/api-client"
import { dayKey, formatDay } from "@/lib/format"
import {
  chatQueries,
  loadOlder,
  markRead,
  useConversationAction,
  useMessageAction,
  useSendMessage,
} from "../api"
import type { ConversationCache } from "../cache"
import { plainText } from "../format/parse"
import type {
  Attachment,
  Conversation,
  LocalAttachment,
  Message,
  MessageStatus,
  ReplyPreview,
} from "../types"
import { cancelFile, retryFile, sendFile } from "../attachments"
import { AttachDialog } from "./attach-dialog"
import { Composer } from "./composer"
import { ImageViewer } from "./image-viewer"
import { ContactDetails } from "./contact-details"
import { ForwardDialog } from "./forward-dialog"
import { MessageInfoDialog } from "./message-info-dialog"
import {
  MessageRow,
  type DisplayMessage,
  type MessageAction,
} from "./message-row"
import { PresenceLine } from "./presence-line"

/** Tela de uma conversa individual (Fase 2): cabeçalho, fixadas, mensagens e campo de envio. */
export function ConversationView({
  conversationId,
  meId,
}: {
  conversationId: string
  meId: string
}) {
  const { t } = useTranslation()
  const conversation = useQuery(chatQueries.detail(conversationId))
  const cache = useQuery(chatQueries.messages(conversationId))

  if (conversation.isPending || cache.isPending) {
    return (
      <div
        className="flex flex-1 flex-col gap-3 p-4"
        role="status"
        aria-label={t("common.loading")}
      >
        <Skeleton className="h-10 w-1/2" />
        <Skeleton className="h-16 w-2/3" />
        <Skeleton className="h-16 w-1/2 self-end" />
      </div>
    )
  }
  if (conversation.isError || cache.isError) {
    const notFound =
      conversation.error instanceof ApiError &&
      conversation.error.status === 404
    return (
      <div className="flex flex-1 items-center justify-center p-6">
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <MessageCircleIcon />
            </EmptyMedia>
            <EmptyTitle>
              <h1>
                {notFound ? t("chat.notFound") : t("errors.genericTitle")}
              </h1>
            </EmptyTitle>
            <EmptyDescription>
              <Link to="/">{t("common.goHome")}</Link>
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      </div>
    )
  }
  return (
    <MessageScrollerProvider
      autoScroll
      defaultScrollPosition={
        conversation.data.unreadCount > 0 ? "last-anchor" : "end"
      }
    >
      <LoadedConversation
        conversation={conversation.data}
        cache={cache.data}
        meId={meId}
      />
    </MessageScrollerProvider>
  )
}

function LoadedConversation({
  conversation,
  cache,
  meId,
}: {
  conversation: Conversation
  cache: ConversationCache
  meId: string
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const scroller = useMessageScroller()
  const send = useSendMessage(conversation.id)
  const messageAction = useMessageAction()
  const conversationAction = useConversationAction()
  const block = useBlock()
  const pinned = useQuery(chatQueries.pinned(conversation.id))
  const [replyTo, setReplyTo] = useState<ReplyPreview | null>(null)
  const [selection, setSelection] = useState<string[] | null>(null)
  const [forwardIds, setForwardIds] = useState<string[]>([])
  const [infoId, setInfoId] = useState<string | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [files, setFiles] = useState<File[]>([])
  const [viewerId, setViewerId] = useState<string | null>(null)
  // Imagens da galeria (MD-03) podem não estar carregadas na conversa: o visualizador usa a lista dela.
  const [viewerImages, setViewerImages] = useState<Attachment[] | null>(null)
  // Painel de detalhes como terceira coluna só em telas largas; nas outras, gaveta (UI-01).
  const wide = useMediaQuery("(min-width: 80rem)")
  // Não lidas no momento em que a conversa abriu: o marcador fica até sair da conversa (MS-14).
  const [unread] = useState(() =>
    conversation.firstUnreadSeq !== null && conversation.unreadCount > 0
      ? { seq: conversation.firstUnreadSeq, count: conversation.unreadCount }
      : null
  )
  const peerName = conversation.peer.label
  const nameOf = (userId: string | null) =>
    userId === meId ? t("chat.you") : peerName

  const lastSeq = cache.messages.at(-1)?.seq ?? 0
  const readRequested = useRef(0)
  // Conversa aberta e aba visível: lê até a última mensagem carregada (e limpa "marcar como não lida").
  useEffect(() => {
    const read = () => {
      if (document.visibilityState !== "visible") {
        return
      }
      const needs =
        (lastSeq > conversation.readSeq || conversation.markedUnread) &&
        readRequested.current < Math.max(lastSeq, 1)
      if (needs) {
        readRequested.current = Math.max(lastSeq, 1)
        void markRead(queryClient, conversation.id, lastSeq).catch(() => {
          readRequested.current = 0
        })
      }
    }
    read()
    document.addEventListener("visibilitychange", read)
    return () => document.removeEventListener("visibilitychange", read)
  }, [
    conversation.id,
    conversation.markedUnread,
    conversation.readSeq,
    lastSeq,
    queryClient,
  ])

  const display = useMemo(
    () => toDisplay(cache, conversation, meId),
    [cache, conversation, meId]
  )
  // Fila de áudios da conversa, em ordem, para tocar em sequência (AU-02).
  const audioQueue = useMemo<Track[]>(
    () =>
      cache.messages
        .filter((message) => message.attachment?.kind === "AUDIO")
        .map((message) => ({
          mediaId: message.attachment!.id,
          conversationId: conversation.id,
          messageId: message.id,
          title: `${message.senderId === meId ? t("chat.you") : peerName} · ${t("media.kind.AUDIO")}`,
        })),
    [cache.messages, conversation.id, meId, peerName, t]
  )
  const images = useMemo(
    () =>
      cache.messages
        .map((message) => message.attachment)
        .filter(
          (attachment): attachment is Attachment => attachment?.kind === "IMAGE"
        ),
    [cache.messages]
  )

  async function jumpTo(messageId: string) {
    for (let page = 0; page < 10; page++) {
      const current = queryClient.getQueryData<ConversationCache>(
        chatQueries.messages(conversation.id).queryKey
      )
      if (current?.messages.some((message) => message.id === messageId)) {
        // Espera o React desenhar a página carregada antes de rolar.
        window.requestAnimationFrame(() =>
          scroller.scrollToMessage(messageId, {
            align: "center",
            behavior: "smooth",
          })
        )
        return
      }
      if (!current?.hasOlder) {
        return
      }
      await loadOlder(queryClient, conversation.id)
    }
  }

  const sendWithAttachment = (input: {
    clientMessageId: string
    body: string
    replyTo: ReplyPreview | null
    attachmentId: string
    attachment: LocalAttachment
  }) =>
    send.mutateAsync(input).catch((error: unknown) =>
      toast.add({
        title: errorMessage(error, t("common.genericError")),
        type: "error",
      })
    )

  /** Escolhidos pelo botão, colados ou arrastados: confirma no diálogo antes de enviar (MD-01). */
  function chooseFiles(chosen: File[]) {
    if (!conversation.canSend) {
      return
    }
    const tooLarge = chosen.filter((file) => file.size > MAX_FILE_SIZE)
    if (tooLarge.length > 0) {
      toast.add({ title: t("media.tooLarge"), type: "error" })
    }
    setFiles(chosen.filter((file) => file.size <= MAX_FILE_SIZE))
  }

  function sendFiles(chosen: File[], caption: string) {
    let previous: Promise<unknown> = Promise.resolve()
    chosen.forEach((file, index) => {
      previous = sendFile(
        queryClient,
        conversation.id,
        file,
        index === 0 ? caption : "",
        index === 0 ? replyTo : null,
        sendWithAttachment,
        crypto.randomUUID(),
        previous
      )
    })
    setFiles([])
    setReplyTo(null)
  }

  function sendBody(
    body: string,
    clientMessageId: string = crypto.randomUUID(),
    reply: ReplyPreview | null = replyTo
  ) {
    send.mutate(
      { clientMessageId, body, replyTo: reply },
      {
        onError: (error) =>
          toast.add({
            title: errorMessage(error, t("common.genericError")),
            type: "error",
          }),
      }
    )
    setReplyTo(null)
  }

  function onAction(message: DisplayMessage, action: MessageAction) {
    if (action === "retry") {
      const pending = cache.pending.find(
        (item) => item.clientMessageId === message.key
      )
      if (pending?.attachment) {
        if (!retryFile(queryClient, pending, sendWithAttachment)) {
          cancelFile(queryClient, conversation.id, pending.clientMessageId)
          toast.add({ title: t("media.retryLost"), type: "error" })
        }
      } else if (pending) {
        sendBody(pending.body, pending.clientMessageId, pending.replyTo)
      }
      return
    }
    if (action === "cancelUpload") {
      cancelFile(queryClient, conversation.id, message.key)
      return
    }
    if (typeof action === "object" && "openImage" in action) {
      setViewerImages(null)
      setViewerId(action.openImage)
      return
    }
    if (!message.id || message.seq === null) {
      return
    }
    const id = message.id
    switch (action) {
      case "reply":
        setReplyTo({
          id,
          seq: message.seq,
          senderId: message.senderId,
          body: message.body,
          attachmentKind: message.attachment?.kind ?? null,
        })
        return
      case "forward":
        setForwardIds([id])
        return
      case "info":
        setInfoId(id)
        return
      case "select":
        setSelection((current) => {
          const list = current ?? []
          return list.includes(id)
            ? list.filter((item) => item !== id)
            : [...list, id]
        })
        return
      case "pin":
      case "unpin":
      case "star":
      case "unstar":
      case "unreact":
        messageAction.mutate({ messageId: id, action })
        return
      default:
        messageAction.mutate({
          messageId: id,
          action: "react",
          emoji: action.react,
        })
    }
  }

  const latestPinned = pinned.data?.at(-1)
  const firstUnreadId =
    unread === null
      ? null
      : (cache.messages.find((message) => message.seq >= unread.seq)?.id ??
        null)
  const canWrite = conversation.canSend && selection === null

  return (
    <div
      className="flex h-full min-h-0 flex-1"
      onDragOver={(event) => {
        if (
          conversation.canSend &&
          event.dataTransfer.types.includes("Files")
        ) {
          event.preventDefault()
          event.dataTransfer.dropEffect = "copy"
        }
      }}
      onDrop={(event) => {
        const dropped = [...event.dataTransfer.files]
        if (dropped.length > 0) {
          event.preventDefault()
          chooseFiles(dropped)
        }
      }}
    >
      <div className="flex h-full min-h-0 min-w-0 flex-1 flex-col">
        <header className="z-10 flex min-h-14 items-center gap-2 border-b bg-background px-2 pt-[env(safe-area-inset-top)] md:px-4">
          <Link
            to={conversation.state === "REQUEST" ? "/requests" : "/"}
            className={buttonVariants({
              variant: "ghost",
              size: "icon",
              className: "md:hidden",
            })}
            aria-label={t("common.back")}
          >
            <ArrowLeftIcon />
          </Link>
          <h1 className="flex min-w-0 flex-1">
            <button
              type="button"
              className="flex min-w-0 flex-1 items-center gap-2 rounded-lg p-1 text-left outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
              aria-expanded={detailsOpen}
              onClick={() => setDetailsOpen((open) => !open)}
            >
              <span className="sr-only">{t("chat.contactInfo")}: </span>
              <UserAvatar
                userId={conversation.peer.userId}
                name={peerName}
                hasPhoto={conversation.peer.hasPhoto}
                version={conversation.peer.photoVersion}
                className="size-9"
              />
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-base font-semibold">
                  {peerName}
                </span>
                <PresenceLine conversation={conversation} />
              </span>
            </button>
          </h1>
        </header>
        {latestPinned ? (
          <button
            type="button"
            onClick={() => void jumpTo(latestPinned.id)}
            className="flex items-center gap-2 border-b bg-muted/50 px-4 py-2 text-left text-sm outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            <PinIcon aria-hidden="true" className="size-4 shrink-0" />
            <span className="sr-only">{t("chat.pinnedMessages")}: </span>
            <span className="truncate">{plainText(latestPinned.body)}</span>
            {pinned.data && pinned.data.length > 1 ? (
              <span className="ml-auto text-xs text-muted-foreground">
                {pinned.data.length}
              </span>
            ) : null}
          </button>
        ) : null}
        {conversation.state === "REQUEST" ? (
          <Alert className="rounded-none border-x-0 border-t-0">
            <AlertDescription className="flex flex-col gap-2">
              <span>{t("chat.requestBanner", { name: peerName })}</span>
              <span className="flex flex-wrap gap-2">
                <Button
                  size="sm"
                  onClick={() =>
                    conversationAction.mutate({
                      conversationId: conversation.id,
                      action: "accept",
                    })
                  }
                >
                  {t("chat.accept")}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={async () => {
                    await conversationAction.mutateAsync({
                      conversationId: conversation.id,
                      action: "decline",
                    })
                    toast.add({ title: t("chat.declined") })
                    await navigate({ to: "/requests" })
                  }}
                >
                  {t("chat.decline")}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={async () => {
                    await block.mutateAsync({
                      userId: conversation.peer.userId,
                      blocked: true,
                    })
                    await conversationAction.mutateAsync({
                      conversationId: conversation.id,
                      action: "decline",
                    })
                    await navigate({ to: "/requests" })
                  }}
                >
                  {t("chat.blockAndDecline")}
                </Button>
              </span>
            </AlertDescription>
          </Alert>
        ) : null}
        <MessageScroller className="min-h-0 flex-1">
          <MessageScrollerViewport
            aria-label={t("chat.messages")}
            preserveScrollOnPrepend
            className="px-2 md:px-4"
          >
            <MessageScrollerContent className="gap-1 py-3">
              {cache.hasOlder ? (
                <div className="flex justify-center pb-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => void loadOlder(queryClient, conversation.id)}
                  >
                    {t("chat.loadOlder")}
                  </Button>
                </div>
              ) : null}
              {display.length === 0 ? (
                <Empty className="flex-1">
                  <EmptyHeader>
                    <EmptyTitle>{t("chat.noMessages")}</EmptyTitle>
                    <EmptyDescription>
                      {t("chat.noMessagesDescription", { name: peerName })}
                    </EmptyDescription>
                  </EmptyHeader>
                </Empty>
              ) : null}
              {display.map((message, index) => {
                const previous = display[index - 1]
                const newDay =
                  !previous ||
                  dayKey(previous.createdAt) !== dayKey(message.createdAt)
                const unreadHere = unread !== null && message.seq === unread.seq
                return (
                  <Fragment key={message.key}>
                    {newDay ? (
                      <Marker
                        variant="separator"
                        className="py-2 text-xs"
                        role="separator"
                      >
                        <MarkerContent>
                          {formatDay(message.createdAt)}
                        </MarkerContent>
                      </Marker>
                    ) : null}
                    {unreadHere ? (
                      <Marker
                        variant="separator"
                        className="py-2 text-xs font-medium text-primary"
                        id="first-unread"
                      >
                        <MarkerContent>
                          {t("chat.unreadMarker", { count: unread.count })}
                        </MarkerContent>
                      </Marker>
                    ) : null}
                    <MessageScrollerItem
                      messageId={message.id ?? message.key}
                      scrollAnchor={unreadHere}
                    >
                      <MessageRow
                        message={message}
                        mine={message.senderId === meId}
                        meId={meId}
                        senderName={nameOf(message.senderId)}
                        nameOf={nameOf}
                        selecting={selection !== null}
                        selected={
                          message.id !== null &&
                          (selection ?? []).includes(message.id)
                        }
                        onAction={(action) => onAction(message, action)}
                        onJump={(id) => void jumpTo(id)}
                        audioQueue={audioQueue}
                      />
                    </MessageScrollerItem>
                  </Fragment>
                )
              })}
            </MessageScrollerContent>
          </MessageScrollerViewport>
          {unread && firstUnreadId ? (
            <Button
              variant="secondary"
              size="sm"
              className="absolute top-2 left-1/2 z-10 -translate-x-1/2 shadow"
              onClick={() => void jumpTo(firstUnreadId)}
            >
              {t("chat.jumpToUnread")}
            </Button>
          ) : null}
          <MessageScrollerButton
            direction="end"
            aria-label={t("chat.messages")}
          />
        </MessageScroller>
        {selection !== null ? (
          <div className="flex items-center gap-2 border-t bg-background px-3 py-2 pb-[max(0.5rem,env(safe-area-inset-bottom))]">
            <Button
              variant="ghost"
              size="icon"
              aria-label={t("chat.cancelSelection")}
              onClick={() => setSelection(null)}
            >
              <XIcon />
            </Button>
            <span className="flex-1 text-sm" aria-live="polite">
              {t("chat.selectedCount", { count: selection.length })}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={selection.length === 0}
              onClick={() => {
                selection.forEach((messageId) =>
                  messageAction.mutate({ messageId, action: "star" })
                )
                setSelection(null)
              }}
            >
              <StarIcon data-icon="inline-start" />
              {t("chat.star")}
            </Button>
            <Button
              size="sm"
              disabled={selection.length === 0}
              onClick={() => setForwardIds(selection)}
            >
              <ForwardIcon data-icon="inline-start" />
              {t("chat.forward")}
            </Button>
          </div>
        ) : conversation.blockedByMe || !conversation.canSend ? (
          <Alert className="rounded-none border-x-0 border-b-0">
            <AlertDescription>
              {conversation.blockedByMe
                ? t("chat.blockedNotice")
                : t("chat.cannotSend")}
            </AlertDescription>
          </Alert>
        ) : (
          <Composer
            conversationId={conversation.id}
            replyTo={replyTo}
            replyName={replyTo ? nameOf(replyTo.senderId) : ""}
            onCancelReply={() => setReplyTo(null)}
            onSend={(body) => sendBody(body)}
            onFiles={chooseFiles}
            onVoice={(file) => sendFiles([file], "")}
            disabled={!canWrite}
          />
        )}
        <ForwardDialog
          messageIds={forwardIds}
          onClose={(sent) => {
            setForwardIds([])
            if (sent) {
              setSelection(null)
            }
          }}
        />
        <MessageInfoDialog messageId={infoId} onClose={() => setInfoId(null)} />
        <AttachDialog
          files={files}
          onClose={() => setFiles([])}
          onSend={sendFiles}
        />
        <ImageViewer
          images={viewerImages ?? images}
          openId={viewerId}
          onOpenChange={setViewerId}
        />
      </div>
      {wide && detailsOpen ? (
        <aside
          aria-label={t("chat.contactInfo")}
          className="w-80 shrink-0 border-l bg-background"
        >
          <ContactDetails
            conversation={conversation}
            onClose={() => setDetailsOpen(false)}
            onOpenImage={(list, id) => {
              setViewerImages(list)
              setViewerId(id)
            }}
          />
        </aside>
      ) : null}
      <Sheet open={!wide && detailsOpen} onOpenChange={setDetailsOpen}>
        <SheetContent
          side="right"
          showCloseButton={false}
          className="w-full gap-0 p-0 pt-[env(safe-area-inset-top)] sm:max-w-sm"
        >
          <SheetTitle className="sr-only">{t("chat.contactInfo")}</SheetTitle>
          <ContactDetails
            conversation={conversation}
            onClose={() => setDetailsOpen(false)}
            onOpenImage={(list, id) => {
              setViewerImages(list)
              setViewerId(id)
            }}
          />
        </SheetContent>
      </Sheet>
    </div>
  )
}

function statusOf(message: Message, conversation: Conversation): MessageStatus {
  if (
    conversation.peerReadSeq !== null &&
    message.seq <= conversation.peerReadSeq
  ) {
    return "read"
  }
  if (message.seq <= conversation.peerDeliveredSeq) {
    return "delivered"
  }
  return "sent"
}

function toDisplay(
  cache: ConversationCache,
  conversation: Conversation,
  meId: string
): DisplayMessage[] {
  const confirmed: DisplayMessage[] = cache.messages.map((message) => ({
    key: message.id,
    id: message.id,
    seq: message.seq,
    senderId: message.senderId,
    body: message.body,
    createdAt: message.createdAt,
    replyTo: message.replyTo,
    forwardCount: message.forwardCount,
    forwardedMany: message.forwardedMany,
    pinned: message.pinnedAt !== null,
    reactions: message.reactions,
    starred: message.starred,
    status: message.senderId === meId ? statusOf(message, conversation) : null,
    attachment: message.attachment,
    localAttachment: null,
  }))
  const pending: DisplayMessage[] = cache.pending.map((message) => ({
    key: message.clientMessageId,
    id: null,
    seq: null,
    senderId: meId,
    body: message.body,
    createdAt: message.createdAt,
    replyTo: message.replyTo,
    forwardCount: 0,
    forwardedMany: false,
    pinned: false,
    reactions: [],
    starred: false,
    status: message.status,
    attachment: null,
    localAttachment: message.attachment,
  }))
  return [...confirmed, ...pending]
}
