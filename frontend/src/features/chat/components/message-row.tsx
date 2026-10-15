import { useTranslation } from "react-i18next"
import {
  CheckCheckIcon,
  CheckIcon,
  ClockIcon,
  CopyIcon,
  CornerUpLeftIcon,
  EllipsisVerticalIcon,
  ForwardIcon,
  InfoIcon,
  ListChecksIcon,
  PinIcon,
  PinOffIcon,
  RotateCwIcon,
  StarIcon,
  StarOffIcon,
  TriangleAlertIcon,
} from "lucide-react"
import { cn } from "cn"

import { Bubble, BubbleContent, BubbleReactions } from "@/components/ui/bubble"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Message, MessageContent } from "@/components/ui/message"
import { toast } from "@/components/ui/toast"
import type { Track } from "@/features/media"
import { formatTime } from "@/lib/format"
import { LinkPreviewCard } from "@/features/linkpreview"
import { MessageText } from "../format/message-text"
import { firstLink, plainText } from "../format/parse"
import type {
  Attachment,
  AttachmentKind,
  LocalAttachment,
  MessageStatus,
  Reaction,
  ReplyPreview,
} from "../types"
import {
  AttachmentView,
  KindIcon,
  LocalAttachmentView,
} from "./attachment-view"

/** Reações rápidas do menu; a lista completa de emojis entra com MS-10. */
export const QUICK_REACTIONS = ["👍", "❤️", "😂", "😮", "😢", "🙏"]

/** Mensagem pronta para exibir: confirmada pelo servidor ou ainda pendente. */
export type DisplayMessage = {
  key: string
  id: string | null
  seq: number | null
  senderId: string | null
  body: string
  createdAt: string
  replyTo: ReplyPreview | null
  forwardCount: number
  forwardedMany: boolean
  pinned: boolean
  reactions: Reaction[]
  starred: boolean
  status: MessageStatus | null
  attachment: Attachment | null
  localAttachment: LocalAttachment | null
}

export type MessageAction =
  | "reply"
  | "forward"
  | "pin"
  | "unpin"
  | "star"
  | "unstar"
  | "info"
  | "select"
  | "retry"
  | "cancelUpload"
  | { react: string }
  | { openImage: string }
  | "unreact"

export function MessageRow({
  message,
  mine,
  meId,
  senderName,
  nameOf,
  selecting,
  selected,
  onAction,
  onJump,
  audioQueue = [],
}: {
  message: DisplayMessage
  mine: boolean
  meId: string
  senderName: string
  nameOf: (userId: string | null) => string
  selecting: boolean
  selected: boolean
  onAction: (action: MessageAction) => void
  onJump: (messageId: string) => void
  /** Áudios da conversa em ordem, para tocar em sequência (AU-02). */
  audioQueue?: Track[]
}) {
  const { t } = useTranslation()
  const confirmed = message.id !== null
  const myReaction = message.reactions.find((r) => r.userId === meId)
  const grouped = groupReactions(message.reactions)

  return (
    <Message
      align={mine ? "end" : "start"}
      className={cn(
        "group/row items-center rounded-lg px-1",
        selecting && "cursor-pointer",
        selected && "bg-primary/10"
      )}
      onClick={selecting && confirmed ? () => onAction("select") : undefined}
    >
      {selecting && confirmed ? (
        <input
          type="checkbox"
          className="size-5 shrink-0 accent-primary"
          checked={selected}
          aria-label={t("chat.selectMessage")}
          onChange={() => onAction("select")}
          onClick={(event) => event.stopPropagation()}
        />
      ) : null}
      <MessageContent className="min-w-0">
        <Bubble
          variant={mine ? "tinted" : "muted"}
          align={mine ? "end" : "start"}
          className={cn("max-w-[min(80%,36rem)]", grouped.length > 0 && "mb-3")}
        >
          <BubbleContent className="flex flex-col gap-1">
            <span className="sr-only">{senderName}: </span>
            {message.forwardCount > 0 ? (
              <span className="flex items-center gap-1 text-xs italic opacity-75">
                <ForwardIcon aria-hidden="true" className="size-3.5" />
                {message.forwardedMany
                  ? t("chat.forwardedMany")
                  : t("chat.forwarded")}
              </span>
            ) : null}
            {message.attachment ? (
              <AttachmentView
                attachment={message.attachment}
                onOpenImage={(id) => onAction({ openImage: id })}
                audio={
                  message.attachment.kind === "AUDIO"
                    ? {
                        track: audioQueue.find(
                          (item) => item.mediaId === message.attachment!.id
                        ) ?? {
                          mediaId: message.attachment.id,
                          conversationId: "",
                          messageId: message.id ?? message.key,
                          title: senderName,
                        },
                        queue: audioQueue,
                      }
                    : undefined
                }
              />
            ) : message.localAttachment ? (
              <LocalAttachmentView
                clientMessageId={message.key}
                attachment={message.localAttachment}
                uploading={message.status === "uploading"}
                onCancel={() => onAction("cancelUpload")}
              />
            ) : null}
            {message.replyTo ? (
              <button
                type="button"
                onClick={(event) => {
                  event.stopPropagation()
                  onJump(message.replyTo!.id)
                }}
                className="flex flex-col rounded-md border-l-4 border-primary bg-black/5 px-2 py-1 text-left text-xs dark:bg-white/10"
              >
                <span className="sr-only">{t("chat.goToOriginal")}: </span>
                <span className="font-medium">
                  {nameOf(message.replyTo.senderId)}
                </span>
                <span className="line-clamp-2 opacity-80">
                  {message.replyTo.body ? (
                    plainText(message.replyTo.body)
                  ) : message.replyTo.attachmentKind ? (
                    <AttachmentLabel kind={message.replyTo.attachmentKind} />
                  ) : null}
                </span>
              </button>
            ) : null}
            {message.body ? <MessageText text={message.body} /> : null}
            {message.id && !message.attachment && firstLink(message.body) ? (
              <LinkPreviewCard url={firstLink(message.body)!} />
            ) : null}
            <span className="flex items-center justify-end gap-1 self-end text-[0.7rem] leading-none opacity-70">
              {message.pinned ? (
                <PinIcon
                  aria-label={t("chat.pinned")}
                  className="size-3"
                  role="img"
                />
              ) : null}
              {message.starred ? (
                <StarIcon
                  aria-label={t("chat.starred")}
                  className="size-3"
                  role="img"
                />
              ) : null}
              <time dateTime={message.createdAt}>
                {formatTime(message.createdAt)}
              </time>
              {mine && message.status ? (
                <StatusIcon status={message.status} />
              ) : null}
            </span>
          </BubbleContent>
          {grouped.length > 0 ? (
            <BubbleReactions
              align={mine ? "end" : "start"}
              aria-label={t("chat.reactions")}
            >
              {grouped.map(([emoji, count]) => (
                <span key={emoji}>
                  {emoji}
                  {count > 1 ? (
                    <span className="ml-0.5 text-xs">{count}</span>
                  ) : null}
                </span>
              ))}
            </BubbleReactions>
          ) : null}
        </Bubble>
      </MessageContent>
      {message.status === "failed" ? (
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("chat.retrySend")}
          onClick={() => onAction("retry")}
        >
          <RotateCwIcon />
        </Button>
      ) : confirmed && !selecting ? (
        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("chat.messageActions")}
                className="shrink-0 opacity-0 group-hover/row:opacity-100 focus-visible:opacity-100 data-popup-open:opacity-100 pointer-coarse:opacity-100"
              />
            }
          >
            <EllipsisVerticalIcon />
          </DropdownMenuTrigger>
          <DropdownMenuContent align={mine ? "end" : "start"}>
            <DropdownMenuGroup className="flex gap-0.5 p-1">
              {QUICK_REACTIONS.map((emoji) => (
                <DropdownMenuItem
                  key={emoji}
                  aria-label={t("chat.react", { emoji })}
                  className={cn(
                    "justify-center px-1.5 text-lg",
                    myReaction?.emoji === emoji && "bg-accent"
                  )}
                  onClick={() =>
                    onAction(
                      myReaction?.emoji === emoji ? "unreact" : { react: emoji }
                    )
                  }
                >
                  {emoji}
                </DropdownMenuItem>
              ))}
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <DropdownMenuItem onClick={() => onAction("reply")}>
                <CornerUpLeftIcon />
                {t("chat.reply")}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onAction("forward")}>
                <ForwardIcon />
                {t("chat.forward")}
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => onAction(message.pinned ? "unpin" : "pin")}
              >
                {message.pinned ? <PinOffIcon /> : <PinIcon />}
                {message.pinned ? t("chat.unpin") : t("chat.pin")}
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => onAction(message.starred ? "unstar" : "star")}
              >
                {message.starred ? <StarOffIcon /> : <StarIcon />}
                {message.starred ? t("chat.unstar") : t("chat.star")}
              </DropdownMenuItem>
              {message.body ? (
                <DropdownMenuItem
                  onClick={() => {
                    void navigator.clipboard
                      ?.writeText(message.body)
                      .then(() => toast.add({ title: t("common.copied") }))
                  }}
                >
                  <CopyIcon />
                  {t("chat.copyText")}
                </DropdownMenuItem>
              ) : null}
              <DropdownMenuItem onClick={() => onAction("select")}>
                <ListChecksIcon />
                {t("chat.select")}
              </DropdownMenuItem>
              {mine ? (
                <DropdownMenuItem onClick={() => onAction("info")}>
                  <InfoIcon />
                  {t("chat.info")}
                </DropdownMenuItem>
              ) : null}
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      ) : null}
    </Message>
  )
}

/** "Foto", "Vídeo", "Áudio" ou "Documento" com ícone (prévias sem texto). */
export function AttachmentLabel({ kind }: { kind: AttachmentKind }) {
  const { t } = useTranslation()
  return (
    <span className="inline-flex items-center gap-1 align-middle">
      <KindIcon kind={kind} className="size-3.5" />
      {t(`media.kind.${kind}`)}
    </span>
  )
}

function groupReactions(reactions: Reaction[]): [string, number][] {
  const counts = new Map<string, number>()
  reactions.forEach((reaction) =>
    counts.set(reaction.emoji, (counts.get(reaction.emoji) ?? 0) + 1)
  )
  return [...counts.entries()]
}

/** Relógio, um tique, dois tiques ou dois tiques azuis (MS-02). */
export function StatusIcon({ status }: { status: MessageStatus }) {
  const { t } = useTranslation()
  const label = t(`chat.status.${status}`)
  const common = { "aria-label": label, role: "img", className: "size-3.5" }
  switch (status) {
    case "uploading":
    case "sending":
      return <ClockIcon {...common} />
    case "failed":
      return (
        <TriangleAlertIcon {...common} className="size-3.5 text-destructive" />
      )
    case "sent":
      return <CheckIcon {...common} />
    case "delivered":
      return <CheckCheckIcon {...common} />
    case "read":
      return (
        <CheckCheckIcon
          {...common}
          className="size-3.5 text-sky-600 dark:text-sky-400"
        />
      )
  }
}
