import { useEffect, useId, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { cn } from "cn"
import {
  PlusIcon,
  SendHorizontalIcon,
  SmileIcon,
  Trash2Icon,
  XIcon,
} from "lucide-react"

import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupInput,
} from "@/components/ui/input-group"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import { Spinner } from "@/components/ui/spinner"
import { EmojiPicker } from "@/features/emoji"
import {
  formatBytes,
  guessKind,
  LocalAudioPlayer,
  pdfCover,
  VideoPlayer,
} from "@/features/media"
import { KindIcon } from "./attachment-view"

/** Um arquivo escolhido e a legenda dele (cada arquivo tem a sua, como no WhatsApp). */
export type ChosenFile = { file: File; caption: string }

type Preview = {
  url: string | null
  /** Capa do PDF (primeira página) e quantas páginas ele tem. */
  cover?: { src: string; pages: number } | null
}

const isPdf = (file: File) =>
  file.type === "application/pdf" || /\.pdf$/i.test(file.name)

/** Endereços locais (blob:) das fotos, vídeos e áudios e a capa dos PDFs, criados uma vez por arquivo. */
function usePreviews(files: File[]) {
  const urls = useMemo(
    () =>
      new Map(
        files.map((file) => [
          file,
          guessKind(file) === "DOCUMENT" ? null : URL.createObjectURL(file),
        ])
      ),
    [files]
  )
  useEffect(
    () => () => urls.forEach((url) => url && URL.revokeObjectURL(url)),
    [urls]
  )
  const [covers, setCovers] = useState(
    () => new Map<File, { src: string; pages: number } | null>()
  )
  useEffect(() => {
    let cancelled = false
    for (const file of files) {
      if (!isPdf(file) || covers.has(file)) {
        continue
      }
      void pdfCover(file, 640)
        .catch(() => null)
        .then((cover) => {
          if (!cancelled) {
            setCovers((current) => new Map(current).set(file, cover))
          }
        })
    }
    return () => {
      cancelled = true
    }
  }, [files, covers])
  return (file: File): Preview => ({
    url: urls.get(file) ?? null,
    cover: isPdf(file) ? covers.get(file) : undefined,
  })
}

/**
 * Prévia dos arquivos antes de enviar, como a do WhatsApp Web (pedido do dono de 2026-10-29): ocupa a conversa abaixo
 * do cabeçalho, com o arquivo escolhido em destaque (foto, vídeo, áudio, a primeira página do PDF ou o ícone do tipo),
 * a legenda dele, as miniaturas embaixo com o "+" para juntar mais e o enviar com a contagem. Fechar com legenda ou
 * mais de um arquivo pergunta se é para descartar. Soltar arquivos na conversa com ela aberta junta à seleção.
 */
export function AttachEditor({
  files,
  allowViewOnce,
  onAdd,
  onRemove,
  onClose,
  onSend,
}: {
  files: File[]
  /** Visualização única não vale para mensagens salvas nem listas de transmissão. */
  allowViewOnce: boolean
  onAdd: (files: File[]) => void
  onRemove: (file: File) => void
  onClose: () => void
  onSend: (chosen: ChosenFile[], viewOnce: boolean) => void
}) {
  const { t, i18n } = useTranslation()
  const [captions, setCaptions] = useState(() => new Map<File, string>())
  const [selected, setSelected] = useState<File | null>(null)
  const [viewOnce, setViewOnce] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [emojiOpen, setEmojiOpen] = useState(false)
  const preview = usePreviews(files)
  const caption = useRef<HTMLInputElement>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const captionId = useId()
  // O primeiro fica em destaque; os que entram depois (pelo "+" ou soltos) ganham o destaque, como no WhatsApp.
  const [count, setCount] = useState(files.length)
  if (files.length !== count) {
    setCount(files.length)
    if (files.length > count) {
      setSelected(files[count])
    }
  }
  const current =
    selected && files.includes(selected) ? selected : (files[0] ?? null)
  const canViewOnce =
    allowViewOnce && files.length === 1 && guessKind(files[0]) !== "DOCUMENT"
  const dirty =
    files.length > 1 || [...captions.values()].some((text) => text.trim())

  useEffect(() => {
    caption.current?.focus()
  }, [current])

  if (!current) {
    return null
  }
  const text = captions.get(current) ?? ""
  const setText = (next: string) =>
    setCaptions((map) => new Map(map).set(current, next))
  const close = () => (dirty ? setConfirming(true) : onClose())
  const send = () =>
    onSend(
      files.map((file) => ({
        file,
        caption: (captions.get(file) ?? "").trim(),
      })),
      canViewOnce && viewOnce
    )
  const kind = guessKind(current)
  const { url, cover } = preview(current)

  return (
    <section
      role="dialog"
      aria-label={t("media.sendFiles", { count: files.length })}
      className="absolute inset-0 z-30 flex animate-in flex-col bg-background duration-(--duration-base) ease-(--ease-enter) fade-in"
      onKeyDown={(event) => {
        if (event.key === "Escape" && !emojiOpen) {
          event.preventDefault()
          close()
        }
      }}
    >
      <div className="flex items-start gap-2 px-(--spacing-gutter) pt-3">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t("common.close")}
          onClick={close}
        >
          <XIcon />
        </Button>
        <div className="flex min-w-0 flex-1 flex-col items-center text-center">
          <p className="max-w-full truncate text-sm font-medium">
            {current.name || t("media.pastedImage")}
          </p>
          <p className="text-xs text-muted-foreground">
            {cover
              ? t("media.pages", { count: cover.pages })
              : formatBytes(current.size, i18n.language)}
          </p>
        </div>
        {/* Tirar o arquivo em destaque: no toque é o único jeito (o X da miniatura é só do mouse). */}
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t("media.removeCurrent")}
          onClick={() => (files.length > 1 ? onRemove(current) : close())}
        >
          <Trash2Icon />
        </Button>
      </div>
      <div className="flex min-h-0 flex-1 items-center justify-center p-(--spacing-gutter)">
        {kind === "IMAGE" && url ? (
          <img
            key={url}
            src={url}
            alt={current.name}
            className="max-h-full max-w-full animate-in rounded-md object-contain duration-(--duration-base) ease-(--ease-enter) zoom-in-95 fade-in"
          />
        ) : kind === "VIDEO" && url ? (
          <VideoPlayer
            key={url}
            src={url}
            label={current.name}
            className="max-h-full max-w-full"
          />
        ) : kind === "AUDIO" && url ? (
          <LocalAudioPlayer
            key={url}
            src={url}
            label={current.name}
            className="w-full max-w-md"
          />
        ) : cover ? (
          <img
            key={cover.src}
            src={cover.src}
            alt={current.name}
            className="max-h-full max-w-full animate-in rounded-sm bg-white object-contain shadow-md duration-(--duration-base) ease-(--ease-enter) zoom-in-95 fade-in"
          />
        ) : cover === undefined && isPdf(current) ? (
          <Spinner className="size-8" />
        ) : (
          <div className="flex flex-col items-center gap-3 text-muted-foreground">
            <KindIcon kind="DOCUMENT" className="size-24" />
            <p className="text-sm">{t("media.noPreview")}</p>
            <p className="text-xs">
              {formatBytes(current.size, i18n.language)}
            </p>
          </div>
        )}
      </div>
      <form
        noValidate
        className="flex flex-col items-center gap-2 px-(--spacing-gutter) pb-3"
        onSubmit={(event) => {
          event.preventDefault()
          send()
        }}
      >
        <div className="flex w-full max-w-2xl items-center gap-2">
          <InputGroup className="h-11 flex-1 rounded-lg bg-card dark:bg-card">
            <InputGroupInput
              ref={caption}
              id={captionId}
              aria-label={t("media.caption")}
              placeholder={t("media.captionPlaceholder")}
              value={text}
              maxLength={4096}
              onChange={(event) => setText(event.target.value)}
            />
            <InputGroupAddon align="inline-end">
              {text ? (
                <InputGroupButton
                  size="icon-sm"
                  aria-label={t("media.clearCaption")}
                  onClick={() => {
                    setText("")
                    caption.current?.focus()
                  }}
                >
                  <XIcon />
                </InputGroupButton>
              ) : null}
              <Popover open={emojiOpen} onOpenChange={setEmojiOpen}>
                <PopoverTrigger
                  render={
                    <InputGroupButton
                      size="icon-sm"
                      aria-label={t("emoji.open")}
                    />
                  }
                >
                  <SmileIcon />
                </PopoverTrigger>
                <PopoverContent side="top" align="end" className="w-auto">
                  <EmojiPicker
                    onSelect={(emoji) => {
                      const field = caption.current
                      const start = field?.selectionStart ?? text.length
                      const end = field?.selectionEnd ?? text.length
                      setText(text.slice(0, start) + emoji + text.slice(end))
                      setEmojiOpen(false)
                      window.requestAnimationFrame(() => {
                        field?.focus()
                        field?.setSelectionRange(
                          start + emoji.length,
                          start + emoji.length
                        )
                      })
                    }}
                  />
                </PopoverContent>
              </Popover>
            </InputGroupAddon>
          </InputGroup>
          {canViewOnce ? (
            // Visualização única (MS-08): o "1" redondo ao lado da legenda, como no WhatsApp.
            <Button
              type="button"
              variant={viewOnce ? "default" : "outline"}
              size="icon"
              role="switch"
              aria-checked={viewOnce}
              aria-label={t("viewOnce.toggle")}
              className="rounded-full font-semibold"
              onClick={() => setViewOnce((on) => !on)}
            >
              1
            </Button>
          ) : null}
        </div>
        {viewOnce ? (
          <p className="text-xs text-muted-foreground">
            {t("viewOnce.screenshotWarning")}
          </p>
        ) : null}
        <div className="flex w-full items-center gap-(--spacing-gutter) border-t pt-3">
          <div className="flex min-w-0 flex-1 justify-center">
            <ul
              data-scroll-x=""
              aria-label={t("media.selectedFiles")}
              className="flex min-w-0 items-center gap-2 overflow-x-auto p-1"
            >
              {files.map((file, index) => (
                <Thumbnail
                  key={`${file.name}-${file.size}-${file.lastModified}-${index}`}
                  file={file}
                  preview={preview(file)}
                  selected={file === current}
                  onSelect={() => setSelected(file)}
                  onRemove={() => onRemove(file)}
                />
              ))}
              <li className="shrink-0">
                <Button
                  type="button"
                  variant="outline"
                  className="size-14"
                  aria-label={t("media.addFiles")}
                  onClick={() => fileInput.current?.click()}
                >
                  <PlusIcon />
                </Button>
              </li>
            </ul>
          </div>
          <div className="relative shrink-0">
            <Button
              type="submit"
              size="icon"
              className="size-14 rounded-full [&_svg:not([class*='size-'])]:size-6"
              aria-label={t("chat.send")}
            >
              <SendHorizontalIcon />
            </Button>
            <span
              aria-hidden="true"
              className="absolute -top-1 -right-1 flex min-w-5 items-center justify-center rounded-full bg-background px-1 text-xs font-semibold text-foreground ring-1 ring-border"
            >
              {files.length}
            </span>
          </div>
        </div>
      </form>
      <input
        ref={fileInput}
        type="file"
        multiple
        hidden
        tabIndex={-1}
        aria-hidden="true"
        onChange={(event) => {
          const chosen = [...(event.target.files ?? [])]
          event.target.value = ""
          if (chosen.length > 0) {
            onAdd(chosen)
          }
        }}
      />
      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent className="sm:max-w-xs">
          <AlertDialogHeader>
            <AlertDialogTitle>{t("media.discardTitle")}</AlertDialogTitle>
          </AlertDialogHeader>
          <AlertDialogFooter className="flex-col sm:flex-col">
            <Button
              variant="outline"
              className="text-destructive"
              onClick={() => {
                setConfirming(false)
                onClose()
              }}
            >
              {t("media.discard")}
            </Button>
            <AlertDialogCancel>{t("common.cancel")}</AlertDialogCancel>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}

function Thumbnail({
  file,
  preview,
  selected,
  onSelect,
  onRemove,
}: {
  file: File
  preview: Preview
  selected: boolean
  onSelect: () => void
  onRemove: () => void
}) {
  const { t } = useTranslation()
  const kind = guessKind(file)
  const name = file.name || t("media.pastedImage")
  return (
    <li className="group/thumb relative shrink-0">
      <button
        type="button"
        aria-label={t("media.showFile", { name })}
        aria-current={selected ? "true" : undefined}
        className={cn(
          "flex size-14 items-center justify-center overflow-hidden rounded-md border-2 border-transparent bg-muted outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
          selected && "border-primary"
        )}
        onClick={onSelect}
      >
        {kind === "IMAGE" && preview.url ? (
          <img src={preview.url} alt="" className="size-full object-cover" />
        ) : kind === "VIDEO" && preview.url ? (
          <video
            src={preview.url}
            muted
            preload="metadata"
            aria-hidden="true"
            className="pointer-events-none size-full object-cover"
          />
        ) : preview.cover ? (
          <img
            src={preview.cover.src}
            alt=""
            className="size-full bg-white object-cover object-top"
          />
        ) : (
          <KindIcon kind={kind} className="size-7 text-muted-foreground" />
        )}
      </button>
      {/* Tirar da seleção: no selecionado sempre; nos outros, ao passar o mouse (no toque, selecione antes). Escondido,
          não pega o clique: no toque o botão tem 44 px e cobriria a miniatura. */}
      <Button
        type="button"
        variant="secondary"
        size="icon-xs"
        aria-label={t("media.removeFile", { name })}
        className={cn(
          "absolute -top-1 -right-1 rounded-full shadow-sm pointer-coarse:hidden",
          !selected &&
            "pointer-events-none opacity-0 focus-visible:opacity-100 [@media(hover:hover)]:group-hover/thumb:pointer-events-auto [@media(hover:hover)]:group-hover/thumb:opacity-100"
        )}
        onClick={onRemove}
      >
        <XIcon />
      </Button>
    </li>
  )
}
