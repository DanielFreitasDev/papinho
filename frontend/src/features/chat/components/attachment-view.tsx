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

import {
  Attachment as AttachmentCard,
  AttachmentActions,
  AttachmentContent,
  AttachmentDescription,
  AttachmentMedia,
  AttachmentTitle,
  AttachmentTrigger,
} from "@/components/ui/attachment"
import { Button, buttonVariants } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import {
  AudioControls,
  formatBytes,
  LocalAudioPlayer,
  mediaUrl,
  VideoPlayer,
  type Track,
} from "@/features/media"
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
 * vídeo e áudio com os tocadores do projeto (UI-05) e documento como cartão de download.
 */
export function AttachmentView({
  attachment,
  onOpenImage,
  onOpenDocument,
  audio,
}: {
  attachment: Attachment
  onOpenImage: (id: string) => void
  /** Abre o PDF no visualizador do app; sem ele, o documento só baixa. */
  onOpenDocument?: (attachment: Attachment) => void
  /** Faixa no tocador global; sem ela, o áudio toca no próprio elemento. */
  audio?: { track: Track; queue: Track[]; received: boolean }
}) {
  const { t } = useTranslation()
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
          data-private="media"
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
              attachment,
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
        <VideoPlayer
          src={mediaUrl(attachment)}
          label={attachment.filename}
          downloadUrl={mediaUrl(attachment, "content", true)}
          privateMedia
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
        <LocalAudioPlayer
          src={mediaUrl(attachment)}
          label={attachment.filename}
          durationMs={attachment.durationMs}
          className="w-64 max-w-full"
        />
      )
    default:
      return (
        <DocumentCard
          attachment={attachment}
          blur={blur}
          onOpen={onOpenDocument}
        />
      )
  }
}

/** O PDF abre no visualizador do app; os outros documentos só baixam. */
export function isPdf(
  attachment: Pick<Attachment, "contentType" | "filename">
) {
  return (
    attachment.contentType === "application/pdf" ||
    /\.pdf$/i.test(attachment.filename)
  )
}

/** "PDF", "DOCX", "ZIP"...: pela extensão do nome, senão pelo tipo. */
function fileType(attachment: Attachment) {
  const extension = /\.([a-z0-9]{1,6})$/i.exec(attachment.filename)?.[1]
  return (extension ?? attachment.contentType.split("/").at(-1) ?? "")
    .slice(0, 6)
    .toUpperCase()
}

/**
 * Documento como no WhatsApp, sobre o Attachment do shadcn (A95): a prévia da primeira página no alto (quando o
 * servidor gerou), o ícone do tipo, o nome e "5 páginas • PDF • 230 KB". Tocar no PDF abre o visualizador do app; nos
 * outros documentos, tocar baixa. O botão de baixar fica por cima da área de toque.
 */
function DocumentCard({
  attachment,
  blur,
  onOpen,
}: {
  attachment: Attachment
  blur: string | null
  onOpen?: (attachment: Attachment) => void
}) {
  const { t, i18n } = useTranslation()
  const [loaded, setLoaded] = useState(false)
  const pdf = isPdf(attachment)
  const type = fileType(attachment)
  const details = [
    attachment.pageCount
      ? t("media.pages", { count: attachment.pageCount })
      : null,
    type || null,
    formatBytes(attachment.size, i18n.language),
  ].filter(Boolean)
  const download = mediaUrl(attachment, "content", true)
  return (
    <AttachmentCard
      data-private="media"
      data-document=""
      className="w-72 max-w-full focus-within:ring-3"
    >
      {attachment.hasThumbnail ? (
        <div className="relative h-36 w-full basis-full overflow-hidden rounded-lg bg-white">
          {blur && !loaded ? (
            <img
              src={blur}
              alt=""
              className="absolute inset-0 size-full object-cover object-top"
            />
          ) : null}
          <img
            src={mediaUrl(attachment, "thumbnail")}
            alt=""
            loading="lazy"
            onLoad={() => setLoaded(true)}
            className="relative block size-full object-cover object-top"
          />
        </div>
      ) : null}
      <AttachmentMedia
        data-file-tone={pdf ? "pdf" : "other"}
        className="items-end pb-1 text-[0.55rem] font-bold text-white"
      >
        {type || <FileIcon aria-hidden="true" />}
      </AttachmentMedia>
      <AttachmentContent>
        <AttachmentTitle>{attachment.filename}</AttachmentTitle>
        <AttachmentDescription>{details.join(" • ")}</AttachmentDescription>
      </AttachmentContent>
      <AttachmentActions>
        <a
          href={download}
          download={attachment.filename}
          aria-label={`${t("media.download")}: ${attachment.filename}`}
          onClick={(event) => event.stopPropagation()}
          className={buttonVariants({ variant: "ghost", size: "icon" })}
        >
          <DownloadIcon />
        </a>
      </AttachmentActions>
      {pdf && onOpen ? (
        <AttachmentTrigger
          onClick={(event) => {
            event.stopPropagation()
            onOpen(attachment)
          }}
        >
          <span className="sr-only">
            {t("media.openDocument", { name: attachment.filename })}
          </span>
        </AttachmentTrigger>
      ) : (
        <AttachmentTrigger
          render={
            <a
              href={download}
              download={attachment.filename}
              tabIndex={-1}
              aria-hidden="true"
              onClick={(event) => event.stopPropagation()}
            />
          }
        />
      )}
    </AttachmentCard>
  )
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
      ) : attachment.kind === "AUDIO" ? (
        // Mesmo formato do áudio enviado, já com a duração medida no aparelho (AU-02).
        <AudioControls
          playing={false}
          position={0}
          duration={(attachment.durationMs ?? 0) / 1000}
          unavailable
          onToggle={() => undefined}
          onSeek={() => undefined}
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
          <Progress
            value={percent}
            aria-label={t("media.uploading", { name: attachment.filename })}
            className="flex-1"
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
