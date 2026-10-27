import { useState } from "react"
import { useInfiniteQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  BellOffIcon,
  ChevronRightIcon,
  ImagesIcon,
  PlayIcon,
  SearchIcon,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import { mediaUrl } from "@/features/media"
import { useErrorToast } from "@/hooks/use-error-toast"
import { chatQueries } from "../api"
import { isMuted, useListAction } from "../organization"
import type { Attachment, Conversation } from "../types"
import { MuteDialog } from "./conversation-menu"

/** Quantas miniaturas a faixa de mídias mostra, como no WhatsApp. */
const STRIP = 4

/** Botão redondo com o nome embaixo, abaixo da foto nos dados do contato ou do grupo ("Pesquisar"). */
export function DetailsSearchButton({ onSearch }: { onSearch: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex justify-center">
      <button
        type="button"
        data-ripple=""
        onClick={onSearch}
        className="group/action flex min-w-20 flex-col items-center gap-1 rounded-xl px-3 py-2 text-xs outline-none focus-visible:ring-3 focus-visible:ring-ring/50 [@media(hover:hover)]:hover:bg-muted"
      >
        <span className="flex size-11 items-center justify-center rounded-full bg-muted transition-transform duration-(--duration-base) ease-(--ease-spring) group-active/action:scale-90">
          <SearchIcon aria-hidden="true" className="size-5" />
        </span>
        {t("details.search")}
      </button>
    </div>
  )
}

/**
 * "Mídia, links e docs" com as últimas fotos e vídeos numa faixa, como no WhatsApp: a linha abre a galeria (MD-03), a
 * miniatura abre a foto no visualizador.
 */
export function MediaStrip({
  conversationId,
  onOpenGallery,
  onOpenImage,
}: {
  conversationId: string
  onOpenGallery: () => void
  onOpenImage: (images: Attachment[], id: string) => void
}) {
  const { t } = useTranslation()
  const gallery = useInfiniteQuery(chatQueries.gallery(conversationId, "MEDIA"))
  const media = (gallery.data?.pages[0]?.messages ?? [])
    .map((message) => message.attachment)
    .filter(
      (attachment): attachment is Attachment =>
        attachment !== null &&
        (attachment.kind === "IMAGE" ||
          (attachment.kind === "VIDEO" && attachment.hasThumbnail))
    )
  const images = media.filter((attachment) => attachment.kind === "IMAGE")
  return (
    <div className="flex flex-col gap-2">
      <Button variant="ghost" className="justify-start" onClick={onOpenGallery}>
        <ImagesIcon data-icon="inline-start" />
        <span className="flex-1 text-left">{t("gallery.title")}</span>
        <ChevronRightIcon data-icon="inline-end" />
      </Button>
      {media.length > 0 ? (
        <ul className="grid grid-cols-4 gap-1.5 px-1">
          {media.slice(0, STRIP).map((attachment) => (
            <li key={attachment.id}>
              <button
                type="button"
                data-ripple=""
                aria-label={t("media.openImage", { name: attachment.filename })}
                onClick={() =>
                  attachment.kind === "IMAGE"
                    ? onOpenImage(images, attachment.id)
                    : onOpenGallery()
                }
                className="relative block aspect-square w-full overflow-hidden rounded-lg bg-muted outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
              >
                <img
                  src={mediaUrl(
                    attachment,
                    attachment.hasThumbnail ? "thumbnail" : "content"
                  )}
                  alt=""
                  loading="lazy"
                  className="size-full object-cover transition-transform duration-(--duration-slow) ease-(--ease-standard) [@media(hover:hover)]:hover:scale-105"
                />
                {attachment.kind === "VIDEO" ? (
                  <PlayIcon
                    aria-hidden="true"
                    className="absolute inset-0 m-auto size-6 text-white drop-shadow"
                  />
                ) : null}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

/** "Silenciar notificações" como interruptor, como no WhatsApp: ligar pergunta por quanto tempo (GR-05). */
export function MuteSwitch({
  conversation,
  name,
}: {
  conversation: Conversation
  name: string
}) {
  const { t } = useTranslation()
  const action = useListAction()
  const onError = useErrorToast()
  const [asking, setAsking] = useState(false)
  const muted = isMuted(conversation)
  return (
    <>
      <label className="flex min-h-11 items-center justify-between gap-3 px-2.5 text-sm">
        <span className="flex items-center gap-2">
          <BellOffIcon aria-hidden="true" className="size-4" />
          {t("details.mute")}
        </span>
        <Switch
          checked={muted || asking}
          disabled={action.isPending}
          onCheckedChange={(on) => {
            if (on) {
              setAsking(true)
              return
            }
            action.mutate(
              {
                conversationId: conversation.id,
                action: "mute",
                duration: null,
                mentions: false,
              },
              { onError }
            )
          }}
        />
      </label>
      {asking ? (
        <MuteDialog
          name={name}
          onOpenChange={setAsking}
          onConfirm={(duration, mentions) =>
            action.mutate(
              {
                conversationId: conversation.id,
                action: "mute",
                duration,
                mentions,
              },
              {
                onSuccess: () => toast.add({ title: t("organize.mutedToast") }),
                onError,
              }
            )
          }
        />
      ) : null}
    </>
  )
}
