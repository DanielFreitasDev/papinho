import { useState } from "react"
import { useInfiniteQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { ArrowLeftIcon, ImagesIcon, PlayIcon } from "lucide-react"
import { cn } from "cn"

import { Button, buttonVariants } from "@/components/ui/button"
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog"
import {
  Empty,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { mediaUrl, VideoPlayer, type Track } from "@/features/media"
import { formatDate } from "@/lib/format"
import { chatQueries, type GalleryTab } from "../api"
import {
  parseMessage,
  plainText,
  type Block,
  type Inline,
} from "../format/parse"
import type { Attachment, Message } from "../types"
import { AttachmentView } from "./attachment-view"

const TABS: GalleryTab[] = ["MEDIA", "LINKS", "DOCUMENTS", "AUDIO"]

/** Galeria da conversa (MD-03): abas de mídia, links, documentos e áudios. */
export function ConversationGallery({
  conversationId,
  onBack,
  onOpenImage,
}: {
  conversationId: string
  onBack: () => void
  onOpenImage: (images: Attachment[], id: string) => void
}) {
  const { t } = useTranslation()
  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex min-h-14 items-center gap-2 border-b px-2">
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("common.back")}
          onClick={onBack}
        >
          <ArrowLeftIcon />
        </Button>
        <h2 className="text-base font-semibold">{t("gallery.title")}</h2>
      </div>
      <Tabs defaultValue="MEDIA" className="min-h-0 flex-1 gap-2 p-2">
        <TabsList className="w-full">
          {TABS.map((tab) => (
            <TabsTrigger key={tab} value={tab}>
              {t(`gallery.tab.${tab}`)}
            </TabsTrigger>
          ))}
        </TabsList>
        {TABS.map((tab) => (
          <TabsContent
            key={tab}
            value={tab}
            className="min-h-0 overflow-y-auto"
          >
            <GalleryTabContent
              conversationId={conversationId}
              tab={tab}
              onOpenImage={onOpenImage}
            />
          </TabsContent>
        ))}
      </Tabs>
    </div>
  )
}

function GalleryTabContent({
  conversationId,
  tab,
  onOpenImage,
}: {
  conversationId: string
  tab: GalleryTab
  onOpenImage: (images: Attachment[], id: string) => void
}) {
  const { t } = useTranslation()
  const query = useInfiniteQuery(chatQueries.gallery(conversationId, tab))
  if (query.isPending) {
    return (
      <div role="status" aria-label={t("common.loading")}>
        <Skeleton className="h-24 w-full" />
      </div>
    )
  }
  if (query.isError) {
    return (
      <p role="alert" className="text-sm text-destructive">
        {t("common.genericError")}
      </p>
    )
  }
  const messages = query.data.pages.flatMap((page) => page.messages)
  if (messages.length === 0) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <ImagesIcon />
          </EmptyMedia>
          <EmptyTitle>{t(`gallery.empty.${tab}`)}</EmptyTitle>
        </EmptyHeader>
      </Empty>
    )
  }
  const images = messages
    .map((message) => message.attachment)
    .filter((item): item is Attachment => item?.kind === "IMAGE")
  // Áudios tocam no tocador global (AU-02), em sequência na ordem da galeria.
  const audioQueue: Track[] = messages
    .filter((message) => message.attachment?.kind === "AUDIO")
    .map((message) => ({
      mediaId: message.attachment!.id,
      conversationId,
      messageId: message.id,
      title: `${t("media.kind.AUDIO")} · ${formatDate(message.createdAt)}`,
    }))
  return (
    <div className="flex flex-col gap-2">
      {tab === "MEDIA" ? (
        <ul className="grid grid-cols-3 gap-1">
          {messages.map((message) =>
            message.attachment ? (
              <li key={message.id}>
                <MediaTile
                  attachment={message.attachment}
                  onOpen={() => onOpenImage(images, message.attachment!.id)}
                />
              </li>
            ) : null
          )}
        </ul>
      ) : tab === "LINKS" ? (
        <ul className="flex flex-col gap-2">
          {messages.map((message) => (
            <LinkItem key={message.id} message={message} />
          ))}
        </ul>
      ) : (
        <ul className="flex flex-col gap-2">
          {messages.map((message) =>
            message.attachment ? (
              <li key={message.id} className="flex flex-col gap-1">
                <AttachmentView
                  attachment={message.attachment}
                  onOpenImage={() => undefined}
                  audio={audioOf(audioQueue, message.id)}
                />
                <span className="text-xs text-muted-foreground">
                  {formatDate(message.createdAt)}
                </span>
              </li>
            ) : null
          )}
        </ul>
      )}
      {query.hasNextPage ? (
        <Button
          variant="outline"
          size="sm"
          disabled={query.isFetchingNextPage}
          onClick={() => void query.fetchNextPage()}
        >
          {t("chat.loadOlder")}
        </Button>
      ) : null}
    </div>
  )
}

function MediaTile({
  attachment,
  onOpen,
}: {
  attachment: Attachment
  onOpen: () => void
}) {
  const { t } = useTranslation()
  const [playing, setPlaying] = useState(false)
  if (attachment.kind === "VIDEO") {
    // Miniatura com o primeiro quadro; o vídeo toca num diálogo com o tocador do projeto (UI-05).
    return (
      <>
        <button
          type="button"
          onClick={() => setPlaying(true)}
          aria-label={t("video.open", { name: attachment.filename })}
          className="relative block aspect-square w-full overflow-hidden rounded-md bg-black outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <video
            src={`${mediaUrl(attachment.id)}#t=0.1`}
            preload="metadata"
            muted
            playsInline
            tabIndex={-1}
            aria-hidden="true"
            className="size-full object-cover"
          />
          <span className="absolute inset-0 flex items-center justify-center">
            <span
              aria-hidden="true"
              className={cn(
                buttonVariants({ variant: "secondary", size: "icon" }),
                "rounded-full"
              )}
            >
              <PlayIcon />
            </span>
          </span>
        </button>
        <Dialog open={playing} onOpenChange={setPlaying}>
          <DialogContent className="max-w-[min(100vw-2rem,48rem)]">
            <DialogTitle className="truncate pr-8">
              {attachment.filename}
            </DialogTitle>
            {playing ? (
              <VideoPlayer
                src={mediaUrl(attachment.id)}
                label={attachment.filename}
                downloadUrl={mediaUrl(attachment.id, "content", true)}
                autoPlay
              />
            ) : null}
          </DialogContent>
        </Dialog>
      </>
    )
  }
  return (
    <button
      type="button"
      onClick={onOpen}
      aria-label={t("media.openImage", { name: attachment.filename })}
      className="block aspect-square w-full overflow-hidden rounded-md bg-muted outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <img
        src={mediaUrl(
          attachment.id,
          attachment.hasThumbnail ? "thumbnail" : "content"
        )}
        alt=""
        loading="lazy"
        className="size-full object-cover"
      />
    </button>
  )
}

function linksOf(blocks: Block[]): string[] {
  const found: string[] = []
  const walk = (inline: Inline[]) =>
    inline.forEach((node) => {
      if (node.type === "link") {
        found.push(node.href)
      } else if ("children" in node) {
        walk(node.children)
      }
    })
  blocks.forEach((block) => {
    if (block.type === "paragraph") {
      walk(block.children)
    } else if (block.type === "quote") {
      found.push(...linksOf(block.children))
    } else if (block.type === "list") {
      block.items.forEach(walk)
    }
  })
  return found
}

function LinkItem({ message }: { message: Message }) {
  const links = linksOf(parseMessage(message.body))
  return (
    <li className="flex flex-col gap-1 rounded-md border p-2 text-sm">
      {links.map((href) => (
        <a
          key={href}
          href={href}
          target="_blank"
          rel="noopener noreferrer nofollow"
          className="truncate font-medium text-primary underline-offset-4 hover:underline"
        >
          {href}
        </a>
      ))}
      <span className="line-clamp-2 text-muted-foreground">
        {plainText(message.body)}
      </span>
    </li>
  )
}

function audioOf(queue: Track[], messageId: string) {
  const track = queue.find((item) => item.messageId === messageId)
  return track ? { track, queue, received: false } : undefined
}
