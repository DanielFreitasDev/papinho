import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  DownloadIcon,
  MaximizeIcon,
  MinimizeIcon,
  PauseIcon,
  PlayIcon,
  Volume2Icon,
  VolumeXIcon,
} from "lucide-react"
import { cn } from "cn"

import { Button, buttonVariants } from "@/components/ui/button"
import { Slider } from "@/components/ui/slider"
import { formatDuration } from "../player"
import { useMediaElement } from "../use-media-element"

/**
 * Vídeo com os controles do projeto (UI-05), no lugar dos `controls` nativos: tocar/pausar (também clicando no
 * vídeo), barra de posição, tempo, som, tela cheia do próprio tocador (os controles continuam os nossos) e,
 * quando permitido, baixar. Sem picture-in-picture nem transmissão remota, que trariam a interface do sistema.
 */
export function VideoPlayer({
  src,
  label,
  autoPlay,
  downloadUrl,
  privateMedia,
  className,
}: {
  src: string
  /** Nome acessível do tocador (o nome do arquivo). */
  label: string
  autoPlay?: boolean
  /** Endereço para baixar; ausente na visualização única. */
  downloadUrl?: string
  /** Marca o vídeo para o desfoque de mídia do modo privado (PV-01). */
  privateMedia?: boolean
  className?: string
}) {
  const { t } = useTranslation()
  const container = useRef<HTMLDivElement>(null)
  const video = useRef<HTMLVideoElement>(null)
  const [state, actions] = useMediaElement(video)
  const [fullscreen, setFullscreen] = useState(false)
  // No iPhone só o <video> entra em tela cheia, com a interface nativa: sem suporte, o botão não aparece.
  const canFullscreen =
    typeof document !== "undefined" && document.fullscreenEnabled

  useEffect(() => {
    const update = () =>
      setFullscreen(document.fullscreenElement === container.current)
    document.addEventListener("fullscreenchange", update)
    return () => document.removeEventListener("fullscreenchange", update)
  }, [])

  function toggleFullscreen() {
    if (document.fullscreenElement) {
      void document.exitFullscreen().catch(() => undefined)
    } else {
      void container.current?.requestFullscreen().catch(() => undefined)
    }
  }

  return (
    <div
      ref={container}
      role="group"
      aria-label={label}
      className={cn(
        "flex w-full flex-col overflow-hidden rounded-md bg-card text-card-foreground",
        fullscreen && "h-full rounded-none",
        className
      )}
      onClick={(event) => event.stopPropagation()}
    >
      <div className="relative flex min-h-0 flex-1 items-center justify-center bg-black">
        <video
          ref={video}
          src={src}
          preload="metadata"
          playsInline
          autoPlay={autoPlay}
          disablePictureInPicture
          disableRemotePlayback
          data-private={privateMedia ? "media" : undefined}
          className={cn("w-full", fullscreen ? "h-full" : "max-h-80")}
          onClick={actions.toggle}
        />
        {/* Atalho de mouse e toque; teclado e leitor de tela usam o botão da barra (sem rótulo duplicado). */}
        {state.playing ? null : (
          <Button
            type="button"
            variant="secondary"
            size="icon-lg"
            aria-hidden="true"
            tabIndex={-1}
            className="absolute rounded-full"
            onClick={actions.toggle}
          >
            <PlayIcon />
          </Button>
        )}
      </div>
      <div className="flex items-center gap-1 px-1 py-1">
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={state.playing ? t("video.pause") : t("video.play")}
          onClick={actions.toggle}
        >
          {state.playing ? <PauseIcon /> : <PlayIcon />}
        </Button>
        <Slider
          className="min-w-12 flex-1 px-1"
          min={0}
          max={state.duration || 1}
          step={0.1}
          value={Math.min(state.position, state.duration || 1)}
          getAriaLabel={() => t("video.position")}
          getAriaValueText={() => formatDuration(state.position)}
          onValueChange={(value) =>
            actions.seek(typeof value === "number" ? value : value[0])
          }
        />
        <span className="px-1 text-xs whitespace-nowrap tabular-nums">
          {formatDuration(state.position)}
          {state.duration ? ` / ${formatDuration(state.duration)}` : ""}
        </span>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={state.muted ? t("video.unmute") : t("video.mute")}
          onClick={actions.toggleMuted}
        >
          {state.muted ? <VolumeXIcon /> : <Volume2Icon />}
        </Button>
        {downloadUrl ? (
          <a
            href={downloadUrl}
            download
            aria-label={t("media.download")}
            className={buttonVariants({ variant: "ghost", size: "icon-sm" })}
          >
            <DownloadIcon />
          </a>
        ) : null}
        {canFullscreen ? (
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={
              fullscreen ? t("video.exitFullscreen") : t("video.fullscreen")
            }
            onClick={toggleFullscreen}
          >
            {fullscreen ? <MinimizeIcon /> : <MaximizeIcon />}
          </Button>
        ) : null}
      </div>
    </div>
  )
}
