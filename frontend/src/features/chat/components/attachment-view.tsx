import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { thumbHashToDataURL } from "thumbhash"
import {
  DownloadIcon,
  FileIcon,
  FileTextIcon,
  ImageIcon,
  MusicIcon,
  VideoIcon,
  XIcon,
} from "lucide-react"
import { cn } from "cn"

import { Button } from "@/components/ui/button"
import { formatBytes, mediaUrl, type Track } from "@/features/media"
import { useUploadProgress } from "../attachments"
import { AudioMessage } from "./audio-message"
import type { Attachment, AttachmentKind, LocalAttachment } from "../types"

/** Ícone por tipo de anexo (lista, prévia de resposta e documentos). */
export function KindIcon({
  kind,
  className,
}: {
  kind: AttachmentKind
  className?: string
}) {
  const props = { "aria-hidden": true, className: cn("size-4", className) }
  switch (kind) {
    case "IMAGE":
      return <ImageIcon {...props} />
    case "VIDEO":
      return <VideoIcon {...props} />
    case "AUDIO":
      return <MusicIcon {...props} />
    default:
      return <FileTextIcon {...props} />
  }
}

function placeholder(thumbhash: string | null): string | null {
  if (!thumbhash) {
    return null
  }
  try {
    const bytes = Uint8Array.from(atob(thumbhash), (char) => char.charCodeAt(0))
    return thumbHashToDataURL(bytes)
  } catch {
    return null
  }
}

/**
 * Anexo de uma mensagem confirmada (MD-01, MD-02): imagem com placeholder ThumbHash que abre o visualizador,
 * vídeo e áudio com controles nativos e documento como cartão de download.
 */
export function AttachmentView({
  attachment,
  onOpenImage,
  audio,
}: {
  attachment: Attachment
  onOpenImage: (id: string) => void
  /** Faixa no tocador global; sem ela, o áudio usa o controle nativo (galeria). */
  audio?: { track: Track; queue: Track[]; received: boolean }
}) {
  const { t, i18n } = useTranslation()
  const blur = useMemo(
    () => placeholder(attachment.thumbhash),
    [attachment.thumbhash]
  )
  const [loaded, setLoaded] = useState(false)
  switch (attachment.kind) {
    case "IMAGE":
      return (
        <button
          type="button"
          className="relative block overflow-hidden rounded-md outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          aria-label={t("media.openImage", { name: attachment.filename })}
          onClick={(event) => {
            event.stopPropagation()
            onOpenImage(attachment.id)
          }}
        >
          {blur && !loaded ? (
            <img
              src={blur}
              alt=""
              width={attachment.width ?? undefined}
              height={attachment.height ?? undefined}
              className="absolute inset-0 size-full object-cover"
            />
          ) : null}
          <img
            src={mediaUrl(
              attachment.id,
              attachment.hasThumbnail ? "thumbnail" : "content"
            )}
            alt=""
            width={attachment.width ?? undefined}
            height={attachment.height ?? undefined}
            loading="lazy"
            onLoad={() => setLoaded(true)}
            className="relative block h-auto max-h-80 w-auto max-w-full object-cover"
          />
        </button>
      )
    case "VIDEO":
      return (
        <video
          controls
          preload="metadata"
          src={mediaUrl(attachment.id)}
          className="max-h-80 w-full rounded-md bg-black"
          aria-label={attachment.filename}
          onClick={(event) => event.stopPropagation()}
        />
      )
    case "AUDIO":
      if (audio) {
        return (
          <AudioMessage
            track={audio.track}
            queue={audio.queue}
            received={audio.received}
          />
        )
      }
      return (
        <audio
          controls
          preload="metadata"
          src={mediaUrl(attachment.id)}
          className="w-64 max-w-full"
          aria-label={attachment.filename}
        />
      )
    default:
      return (
        <a
          href={mediaUrl(attachment.id, "content", true)}
          download={attachment.filename}
          onClick={(event) => event.stopPropagation()}
          className="flex min-w-0 items-center gap-3 rounded-md bg-black/5 p-2 outline-none hover:bg-black/10 focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-white/10"
        >
          <FileIcon aria-hidden="true" className="size-8 shrink-0" />
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="truncate font-medium">{attachment.filename}</span>
            <span className="text-xs opacity-80">
              {formatBytes(attachment.size, i18n.language)}
            </span>
          </span>
          <DownloadIcon aria-hidden="true" className="size-5 shrink-0" />
          <span className="sr-only">{t("media.download")}</span>
        </a>
      )
  }
}

/** Arquivo ainda subindo: prévia local, progresso e cancelar. */
export function LocalAttachmentView({
  clientMessageId,
  attachment,
  uploading,
  onCancel,
}: {
  clientMessageId: string
  attachment: LocalAttachment
  uploading: boolean
  onCancel: () => void
}) {
  const { t, i18n } = useTranslation()
  const fraction = useUploadProgress(clientMessageId)
  const percent = Math.round(fraction * 100)
  return (
    <div className="flex flex-col gap-1">
      {attachment.kind === "IMAGE" && attachment.previewUrl ? (
        <img
          src={attachment.previewUrl}
          alt=""
          className="block h-auto max-h-80 w-auto max-w-full rounded-md object-cover opacity-80"
        />
      ) : attachment.kind === "VIDEO" && attachment.previewUrl ? (
        <video
          src={attachment.previewUrl}
          muted
          className="max-h-80 w-full rounded-md bg-black opacity-80"
        />
      ) : (
        <span className="flex min-w-0 items-center gap-3 rounded-md bg-black/5 p-2 dark:bg-white/10">
          <KindIcon kind={attachment.kind} className="size-8 shrink-0" />
          <span className="flex min-w-0 flex-col">
            <span className="truncate font-medium">{attachment.filename}</span>
            <span className="text-xs opacity-80">
              {formatBytes(attachment.size, i18n.language)}
            </span>
          </span>
        </span>
      )}
      {uploading ? (
        <span className="flex items-center gap-2">
          <progress
            value={percent}
            max={100}
            aria-label={t("media.uploading", { name: attachment.filename })}
            className="h-1.5 flex-1 overflow-hidden rounded-full accent-primary"
          />
          <span className="text-xs tabular-nums" aria-hidden="true">
            {percent}%
          </span>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={t("media.cancelUpload")}
            onClick={(event) => {
              event.stopPropagation()
              onCancel()
            }}
          >
            <XIcon />
          </Button>
        </span>
      ) : null}
    </div>
  )
}
