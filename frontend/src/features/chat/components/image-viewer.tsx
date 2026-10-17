import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  DownloadIcon,
  XIcon,
  ZoomInIcon,
  ZoomOutIcon,
} from "lucide-react"
import { cn } from "cn"

import { Button, buttonVariants } from "@/components/ui/button"
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog"
import { mediaUrl } from "@/features/media"
import type { Attachment } from "../types"

/**
 * Visualizador de imagens em tela cheia (MD-02): zoom (botão, clique duplo ou + e -), navegação entre as
 * imagens carregadas da conversa (botões e setas do teclado) e download. Esc fecha.
 */
export function ImageViewer({
  images,
  openId,
  onOpenChange,
}: {
  images: Attachment[]
  openId: string | null
  onOpenChange: (id: string | null) => void
}) {
  const { t } = useTranslation()
  const [zoom, setZoom] = useState(false)
  const index = images.findIndex((image) => image.id === openId)
  const current = index >= 0 ? images[index] : null

  function go(step: number) {
    const next = images[index + step]
    if (next) {
      setZoom(false)
      onOpenChange(next.id)
    }
  }

  useEffect(() => {
    if (!current) {
      return
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "ArrowLeft") {
        go(-1)
      } else if (event.key === "ArrowRight") {
        go(1)
      } else if (event.key === "+" || event.key === "=") {
        setZoom(true)
      } else if (event.key === "-") {
        setZoom(false)
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  })

  return (
    <Dialog
      open={current !== null}
      onOpenChange={(open) => {
        if (!open) {
          setZoom(false)
          onOpenChange(null)
        }
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="flex h-dvh max-h-dvh w-screen max-w-none flex-col gap-0 rounded-none border-0 bg-black p-0 text-white sm:max-w-none"
      >
        <div className="flex items-center gap-1 p-2 pt-[max(0.5rem,env(safe-area-inset-top))]">
          <DialogTitle className="min-w-0 flex-1 truncate px-2 text-sm font-medium text-white">
            {current?.filename}
            {images.length > 1 ? (
              <span className="ml-2 text-white/70">
                {t("media.position", {
                  current: index + 1,
                  total: images.length,
                })}
              </span>
            ) : null}
          </DialogTitle>
          <Button
            variant="ghost"
            size="icon"
            className="text-white hover:bg-white/10 hover:text-white"
            aria-label={zoom ? t("media.zoomOut") : t("media.zoomIn")}
            aria-pressed={zoom}
            onClick={() => setZoom((value) => !value)}
          >
            {zoom ? <ZoomOutIcon /> : <ZoomInIcon />}
          </Button>
          {current ? (
            <a
              href={mediaUrl(current, "content", true)}
              download={current.filename}
              aria-label={t("media.download")}
              className={buttonVariants({
                variant: "ghost",
                size: "icon",
                className: "text-white hover:bg-white/10 hover:text-white",
              })}
            >
              <DownloadIcon />
            </a>
          ) : null}
          <Button
            variant="ghost"
            size="icon"
            className="text-white hover:bg-white/10 hover:text-white"
            aria-label={t("common.close")}
            onClick={() => onOpenChange(null)}
          >
            <XIcon />
          </Button>
        </div>
        <div
          className={cn(
            "relative flex min-h-0 flex-1 items-center justify-center",
            zoom ? "overflow-auto" : "overflow-hidden"
          )}
        >
          {current ? (
            <img
              key={current.id}
              src={mediaUrl(current)}
              alt={current.filename}
              width={current.width ?? undefined}
              height={current.height ?? undefined}
              onDoubleClick={() => setZoom((value) => !value)}
              className={cn(
                "select-none",
                zoom
                  ? "max-h-none max-w-none cursor-zoom-out"
                  : "h-auto max-h-full w-auto max-w-full cursor-zoom-in object-contain"
              )}
            />
          ) : null}
          {index > 0 ? (
            <Button
              variant="secondary"
              size="icon"
              className="absolute top-1/2 left-2 -translate-y-1/2"
              aria-label={t("media.previous")}
              onClick={() => go(-1)}
            >
              <ChevronLeftIcon />
            </Button>
          ) : null}
          {index >= 0 && index < images.length - 1 ? (
            <Button
              variant="secondary"
              size="icon"
              className="absolute top-1/2 right-2 -translate-y-1/2"
              aria-label={t("media.next")}
              onClick={() => go(1)}
            >
              <ChevronRightIcon />
            </Button>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  )
}
