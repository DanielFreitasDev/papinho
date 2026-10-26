import { memo, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  BanIcon,
  BellOffIcon,
  CheckCheckIcon,
  CheckIcon,
  ClockIcon,
  CopyIcon,
  CornerUpLeftIcon,
  EllipsisVerticalIcon,
  EyeIcon,
  EyeOffIcon,
  ForwardIcon,
  InfoIcon,
  ListChecksIcon,
  MessageCircleReplyIcon,
  PencilIcon,
  PinIcon,
  PinOffIcon,
  PlusIcon,
  SmileIcon,
  SmilePlusIcon,
  RotateCwIcon,
  StarIcon,
  StarOffIcon,
  Trash2Icon,
  TriangleAlertIcon,
} from "lucide-react"
import { cn } from "cn"

import { useContextMenu } from "@/hooks/use-context-menu"
import { Bubble, BubbleContent, BubbleReactions } from "@/components/ui/bubble"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
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
import { mediaUrl, type Track } from "@/features/media"
import { formatTime } from "@/lib/format"
import { LinkPreviewCard } from "@/features/linkpreview"
import { MessageText } from "../format/message-text"
import type { SearchTerms } from "../format/search-match"
import { PollView } from "./poll-view"
import { firstLink, plainText } from "../format/parse"
import type {
  Attachment,
  AttachmentKind,
  LocalAttachment,
  MessageKind,
  MessageStatus,
  Poll,
  ViewOnce,
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
  /** Id gerado por quem enviou: liga a bolha pendente à confirmada. */
  clientMessageId: string | null
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
  editedAt: string | null
  /** Apagada para todos (MS-06). */
  deleted: boolean
  kind: MessageKind
  poll: Poll | null
  viewOnce: ViewOnce | null
  silent: boolean
  sticker: { id: string; mediaId: string } | null
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
  | "edit"
  | "delete"
  | "replyPrivately"
  | { react: string }
  | { openImage: string }
  | "moreReactions"
  | "openViewOnce"
  | "unreact"

/**
 * Uma linha da conversa. Memoizada (UI-04): ao carregar páginas antigas ou chegar mensagem nova, só as linhas que
 * mudaram desenham de novo. Por isso os callbacks recebidos precisam ser estáveis (sempre a lógica atual).
 */
export const MessageRow = memo(MessageRowView, sameRow)

function sameRow(
  before: Parameters<typeof MessageRowView>[0],
  after: Parameters<typeof MessageRowView>[0]
) {
  const keys = Object.keys({ ...before, ...after }) as (keyof typeof before)[]
  return keys.every((key) =>
    key === "message"
      ? shallowEqual(before.message, after.message)
      : Object.is(before[key], after[key])
  )
}

function shallowEqual(before: DisplayMessage, after: DisplayMessage) {
  const keys = Object.keys({ ...before, ...after }) as (keyof DisplayMessage)[]
  return keys.every((key) => Object.is(before[key], after[key]))
}

function MessageRowView({
  message,
  mine,
  meId,
  senderName,
  nameOf,
  selecting,
  selected,
  onMessageAction,
  onJump,
  audioQueue = [],
  conversationId,
  canEdit = false,
  showSender = false,
  canReplyPrivately = false,
  highlight = null,
  flash = false,
}: {
  message: DisplayMessage
  mine: boolean
  meId: string
  senderName: string
  nameOf: (userId: string | null) => string
  selecting: boolean
  selected: boolean
  /** Estável: recebe a mensagem e a ação. */
  onMessageAction: (message: DisplayMessage, action: MessageAction) => void
  onJump: (messageId: string) => void
  /** Áudios da conversa em ordem, para tocar em sequência (AU-02). */
  audioQueue?: Track[]
  /** Conversa aberta, para saber se a citação é desta conversa. */
  conversationId?: string
  /** Ainda dentro da janela de edição (MS-05). */
  canEdit?: boolean
  /** Em grupo, o nome de quem mandou aparece no balão (GR-01). */
  showSender?: boolean
  /** Mensagem de outra pessoa num grupo: "Responder em privado". */
  canReplyPrivately?: boolean
  /** Busca aberta na conversa: os trechos achados ganham o marca-texto (GR-06). Estável entre desenhos. */
  highlight?: SearchTerms | null
  /** Destino de um salto (resultado da busca, citação): a bolha pisca uma vez. */
  flash?: boolean
}) {
  const { t } = useTranslation()
  // Reações que já estavam quando a linha montou entram paradas; as que chegam depois, com o "pop".
  const [initialReactions] = useState(
    () => new Set(message.reactions.map((reaction) => reaction.emoji))
  )
  // Botão direito abre o menu da mensagem onde o mouse está (no toque, o menu ⋮).
  const menu = useContextMenu()
  const onAction = (action: MessageAction) => onMessageAction(message, action)
  const confirmed = message.id !== null
  if (message.deleted) {
    return (
      <DeletedRow
        message={message}
        mine={mine}
        senderName={senderName}
        selecting={selecting}
        selected={selected}
        onAction={onAction}
      />
    )
  }
  const myReaction = message.reactions.find((r) => r.userId === meId)
  const grouped = groupReactions(message.reactions)

  return (
    <Message
      align={mine ? "end" : "start"}
      data-private-scope=""
      className={cn(
        "group/row items-center rounded-lg px-1",
        selecting && "cursor-pointer",
        selected && "bg-primary/10"
      )}
      onClick={selecting && confirmed ? () => onAction("select") : undefined}
      onContextMenu={
        confirmed && !selecting && message.status !== "failed"
          ? menu.onContextMenu
          : undefined
      }
    >
      {selecting && confirmed ? (
        <Checkbox
          className="shrink-0"
          checked={selected}
          aria-label={t("chat.selectMessage")}
          onCheckedChange={() => onAction("select")}
          onClick={(event) => event.stopPropagation()}
        />
      ) : null}
      {/* Largura do conteúdo (até 80%): os atalhos ficam colados na bolha, não na borda da conversa. */}
      <MessageContent className="w-auto max-w-[min(80%,36rem)] min-w-0">
        <Bubble
          variant={mine ? "tinted" : "muted"}
          align={mine ? "end" : "start"}
          className={cn("max-w-full", grouped.length > 0 && "mb-3")}
          data-flash={flash ? "" : undefined}
        >
          <BubbleContent
            className="flex flex-col gap-1"
            data-private="messages"
          >
            {showSender && !mine ? (
              <span className="text-xs font-semibold text-primary">
                {senderName}
              </span>
            ) : (
              <span className="sr-only">{senderName}: </span>
            )}
            {message.forwardCount > 0 ? (
              <span className="flex items-center gap-1 text-xs italic opacity-75">
                <ForwardIcon aria-hidden="true" className="size-3.5" />
                {message.forwardedMany
                  ? t("chat.forwardedMany")
                  : t("chat.forwarded")}
              </span>
            ) : null}
            {message.sticker ? (
              <img
                src={mediaUrl(message.sticker.mediaId)}
                alt={t("sticker.label")}
                className="size-32 object-contain"
              />
            ) : message.viewOnce ? (
              <ViewOnceBubble
                viewOnce={message.viewOnce}
                mine={mine}
                onOpen={() => onAction("openViewOnce")}
              />
            ) : message.attachment ? (
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
                          grant: message.attachment.grant,
                          conversationId: "",
                          messageId: message.id ?? message.key,
                          title: senderName,
                          durationMs: message.attachment.durationMs,
                        },
                        queue: audioQueue,
                        received: !mine,
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
                  // Citação de outra conversa ("responder em privado") não tem para onde saltar.
                  const origin = message.replyTo!.conversationId
                  if (!origin || origin === conversationId) {
                    onJump(message.replyTo!.id)
                  }
                }}
                className="flex flex-col rounded-md border-l-4 border-primary bg-black/5 px-2 py-1 text-left text-xs dark:bg-white/10"
              >
                <span className="sr-only">{t("chat.goToOriginal")}: </span>
                <span className="font-medium">
                  {nameOf(message.replyTo.senderId)}
                </span>
                <span className="line-clamp-2 opacity-80">
                  {message.replyTo.deleted ? (
                    <span className="italic">{t("chat.deleted")}</span>
                  ) : message.replyTo.body ? (
                    plainText(message.replyTo.body)
                  ) : message.replyTo.attachmentKind ? (
                    <AttachmentLabel kind={message.replyTo.attachmentKind} />
                  ) : null}
                </span>
              </button>
            ) : null}
            {message.poll ? (
              <PollView
                messageId={message.id}
                question={message.body}
                poll={message.poll}
                nameOf={nameOf}
              />
            ) : message.body ? (
              <MessageText text={message.body} highlight={highlight} />
            ) : null}
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
              {message.silent ? (
                <BellOffIcon
                  aria-label={t("chat.silent")}
                  className="size-3"
                  role="img"
                />
              ) : null}
              {message.editedAt ? <span>{t("chat.edited")}</span> : null}
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
                <ReactionChip
                  key={emoji}
                  emoji={emoji}
                  count={count}
                  arrived={!initialReactions.has(emoji)}
                />
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
        <>
          {/* Atalhos ao lado da bolha, como no WhatsApp Web: só com mouse; no toque ficam no menu ⋮. */}
          {message.kind === "USER" && !message.viewOnce ? (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t("chat.forward")}
              className={HOVER_ACTION}
              onClick={() => onAction("forward")}
            >
              <ForwardIcon />
            </Button>
          ) : null}
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t("chat.reactTo")}
                  className={HOVER_ACTION}
                />
              }
            >
              <SmileIcon />
            </DropdownMenuTrigger>
            <DropdownMenuContent align={mine ? "end" : "start"}>
              <QuickReactions
                compact
                myEmoji={myReaction?.emoji}
                onAction={onAction}
              />
            </DropdownMenuContent>
          </DropdownMenu>
          <DropdownMenu open={menu.open} onOpenChange={menu.onOpenChange}>
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
            <DropdownMenuContent
              align={menu.anchor ? "start" : mine ? "end" : "start"}
              anchor={menu.anchor}
            >
              <QuickReactions myEmoji={myReaction?.emoji} onAction={onAction} />
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <DropdownMenuItem onClick={() => onAction("reply")}>
                  <CornerUpLeftIcon />
                  {t("chat.reply")}
                </DropdownMenuItem>
                {canReplyPrivately ? (
                  <DropdownMenuItem onClick={() => onAction("replyPrivately")}>
                    <MessageCircleReplyIcon />
                    {t("groups.replyPrivately")}
                  </DropdownMenuItem>
                ) : null}
                {message.kind === "USER" && !message.viewOnce ? (
                  <DropdownMenuItem onClick={() => onAction("forward")}>
                    <ForwardIcon />
                    {t("chat.forward")}
                  </DropdownMenuItem>
                ) : null}
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
                {mine && canEdit ? (
                  <DropdownMenuItem onClick={() => onAction("edit")}>
                    <PencilIcon />
                    {t("chat.edit")}
                  </DropdownMenuItem>
                ) : null}
                {mine ? (
                  <DropdownMenuItem onClick={() => onAction("info")}>
                    <InfoIcon />
                    {t("chat.info")}
                  </DropdownMenuItem>
                ) : null}
                <DropdownMenuItem
                  variant="destructive"
                  onClick={() => onAction("delete")}
                >
                  <Trash2Icon />
                  {t("chat.delete")}
                </DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>
        </>
      ) : null}
    </Message>
  )
}

/** Atalho que só aparece com o ponteiro sobre a linha (no toque, as ações ficam no menu ⋮). */
const HOVER_ACTION =
  "hidden shrink-0 group-hover/row:inline-flex data-popup-open:inline-flex"

/** Reações rápidas e "Mais reações", no atalho ao lado da bolha e no menu ⋮. */
function QuickReactions({
  myEmoji,
  onAction,
  compact = false,
}: {
  myEmoji: string | undefined
  onAction: (action: MessageAction) => void
  /** Uma fileira só, com "+" no fim (atalho ao lado da bolha, como no WhatsApp Web). */
  compact?: boolean
}) {
  const { t } = useTranslation()
  const more = (
    <DropdownMenuItem
      aria-label={compact ? t("emoji.moreReactions") : undefined}
      className={compact ? "justify-center px-1.5" : undefined}
      onClick={() => onAction("moreReactions")}
    >
      {compact ? <PlusIcon /> : <SmilePlusIcon />}
      {compact ? null : t("emoji.moreReactions")}
    </DropdownMenuItem>
  )
  return (
    <>
      <DropdownMenuGroup className="flex gap-0.5 p-1">
        {QUICK_REACTIONS.map((emoji) => (
          <DropdownMenuItem
            key={emoji}
            aria-label={t("chat.react", { emoji })}
            className={cn(
              "justify-center px-1.5 text-lg",
              myEmoji === emoji && "bg-accent"
            )}
            onClick={() =>
              onAction(myEmoji === emoji ? "unreact" : { react: emoji })
            }
          >
            {emoji}
          </DropdownMenuItem>
        ))}
        {compact ? more : null}
      </DropdownMenuGroup>
      {compact ? null : <DropdownMenuGroup>{more}</DropdownMenuGroup>}
    </>
  )
}

/** Aviso "Esta mensagem foi apagada" (MS-06); ainda pode ser apagado para mim ou selecionado. */
function DeletedRow({
  message,
  mine,
  senderName,
  selecting,
  selected,
  onAction,
}: {
  message: DisplayMessage
  mine: boolean
  senderName: string
  selecting: boolean
  selected: boolean
  onAction: (action: MessageAction) => void
}) {
  const { t } = useTranslation()
  return (
    <Message
      align={mine ? "end" : "start"}
      data-private-scope=""
      className={cn(
        "group/row items-center rounded-lg px-1",
        selecting && "cursor-pointer",
        selected && "bg-primary/10"
      )}
      onClick={selecting ? () => onAction("select") : undefined}
    >
      {selecting ? (
        <Checkbox
          className="shrink-0"
          checked={selected}
          aria-label={t("chat.selectMessage")}
          onCheckedChange={() => onAction("select")}
          onClick={(event) => event.stopPropagation()}
        />
      ) : null}
      <MessageContent className="min-w-0">
        <Bubble
          variant={mine ? "tinted" : "muted"}
          align={mine ? "end" : "start"}
          className="max-w-[min(80%,36rem)]"
        >
          <BubbleContent className="flex items-center gap-2 text-sm italic opacity-80">
            <span className="sr-only">{senderName}: </span>
            <BanIcon aria-hidden="true" className="size-4 shrink-0" />
            <span>{mine ? t("chat.deletedByMe") : t("chat.deleted")}</span>
            <time
              dateTime={message.createdAt}
              className="ml-2 self-end text-[0.7rem] leading-none not-italic"
            >
              {formatTime(message.createdAt)}
            </time>
          </BubbleContent>
        </Bubble>
      </MessageContent>
      {selecting ? null : (
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
            <DropdownMenuItem onClick={() => onAction("select")}>
              <ListChecksIcon />
              {t("chat.select")}
            </DropdownMenuItem>
            <DropdownMenuItem
              variant="destructive"
              onClick={() => onAction("delete")}
            >
              <Trash2Icon />
              {t("chat.delete")}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </Message>
  )
}

/** Anexo de visualização única (MS-08): abrir uma vez, "aberta" ou, para quem enviou, quantos abriram. */
function ViewOnceBubble({
  viewOnce,
  mine,
  onOpen,
}: {
  viewOnce: ViewOnce
  mine: boolean
  onOpen: () => void
}) {
  const { t } = useTranslation()
  const label = t(`viewOnce.kind.${viewOnce.kind}`)
  if (!mine && !viewOnce.openedByMe && !viewOnce.expired) {
    return (
      <Button
        variant="outline"
        size="sm"
        className="self-start"
        onClick={(event) => {
          event.stopPropagation()
          onOpen()
        }}
      >
        <EyeIcon data-icon="inline-start" />
        {t("viewOnce.open", { kind: label })}
      </Button>
    )
  }
  return (
    <span className="flex items-center gap-2 text-sm italic opacity-80">
      <EyeOffIcon aria-hidden="true" className="size-4" />
      {mine
        ? viewOnce.openedCount > 0
          ? t("viewOnce.openedBy", { kind: label, count: viewOnce.openedCount })
          : t("viewOnce.sent", { kind: label })
        : t("viewOnce.opened", { kind: label })}
    </span>
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

/** Emoji de uma reação com a contagem; a que chega depois da linha montar e a contagem que muda, com o "pop". */
function ReactionChip({
  emoji,
  count,
  arrived,
}: {
  emoji: string
  count: number
  arrived: boolean
}) {
  const [initialCount] = useState(count)
  return (
    <span className={cn(arrived && "animate-pop")}>
      {emoji}
      {count > 1 ? (
        <span
          key={count}
          className={cn(
            "ml-0.5 inline-block text-xs",
            count !== initialCount && "animate-pop"
          )}
        >
          {count}
        </span>
      ) : null}
    </span>
  )
}

/**
 * Relógio, um tique, dois tiques ou dois tiques azuis (MS-02). A troca de estado chega com um "pop"; o estado de
 * quando a linha montou, não (a lista não pisca ao rolar).
 */
export function StatusIcon({ status }: { status: MessageStatus }) {
  const { t } = useTranslation()
  const [initial] = useState(status)
  const label = t(`chat.status.${status}`)
  const pop = status !== initial && "animate-pop"
  const common = { "aria-label": label, role: "img" }
  switch (status) {
    case "uploading":
    case "sending":
      return (
        <ClockIcon key={status} {...common} className={cn("size-3.5", pop)} />
      )
    case "failed":
      return (
        <TriangleAlertIcon
          key={status}
          {...common}
          className={cn("size-3.5 text-destructive", pop)}
        />
      )
    case "sent":
      return (
        <CheckIcon key={status} {...common} className={cn("size-3.5", pop)} />
      )
    case "delivered":
      return (
        <CheckCheckIcon
          key={status}
          {...common}
          className={cn("size-3.5", pop)}
        />
      )
    case "read":
      return (
        <CheckCheckIcon
          key={status}
          {...common}
          className={cn("size-3.5 text-sky-600 dark:text-sky-400", pop)}
        />
      )
  }
}
