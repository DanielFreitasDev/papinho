import { useRef } from "react"
import { useTranslation } from "react-i18next"
import { PauseIcon, PlayIcon } from "lucide-react"
import { cn } from "cn"

import { Button } from "@/components/ui/button"
import { Slider } from "@/components/ui/slider"
import { formatDuration, formatRate } from "../player"
import { useMediaElement } from "../use-media-element"

/**
 * Controles de áudio do projeto (AU-02, UI-05): tocar/pausar, barra de posição, tempo e velocidade. Servem ao
 * tocador global das mensagens e aos áudios com elemento próprio (pré-escuta e visualização única). O tempo é um
 * só: o decorrido enquanto toca; parado, pausado ou no fim, a duração total, que já vem do arquivo antes do
 * primeiro play (AU-02).
 */
export function AudioControls({
  playing,
  position,
  duration,
  rate,
  disabled,
  unavailable,
  onToggle,
  onSeek,
  onCycleRate,
  className,
}: {
  playing: boolean
  position: number
  /** Em segundos; 0 quando ainda não se sabe (mensagem antiga, antes de o navegador carregar). */
  duration: number
  rate?: number
  /** Barra de posição inativa (o áudio não é a faixa atual do tocador). */
  disabled?: boolean
  /** Ainda subindo: nada toca e a barra fica parada. */
  unavailable?: boolean
  onToggle: () => void
  onSeek: (seconds: number) => void
  /** Sem ela, o botão de velocidade não aparece. */
  onCycleRate?: () => void
  className?: string
}) {
  const { t, i18n } = useTranslation()
  const shownRate = rate === undefined ? null : formatRate(rate, i18n.language)
  const elapsed = playing || duration <= 0
  return (
    <div className={cn("flex w-64 max-w-full items-center gap-2", className)}>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        aria-label={playing ? t("audio.pause") : t("audio.play")}
        disabled={unavailable}
        onClick={onToggle}
      >
        {playing ? <PauseIcon /> : <PlayIcon />}
      </Button>
      <span className="flex min-w-0 flex-1 flex-col gap-1.5 pt-1.5">
        <Slider
          min={0}
          max={duration || 1}
          step={0.1}
          value={Math.min(position, duration || 1)}
          disabled={disabled || unavailable}
          getAriaLabel={() => t("audio.position")}
          getAriaValueText={() => formatDuration(position)}
          onValueChange={(value) =>
            onSeek(typeof value === "number" ? value : value[0])
          }
        />
        <span className="text-xs tabular-nums opacity-80">
          {unavailable && duration <= 0 ? (
            // Medindo a gravação: sem "0:00" piscando antes da duração.
            "\u00a0"
          ) : (
            <>
              <span className="sr-only">
                {elapsed ? t("audio.elapsed") : t("audio.duration")}{" "}
              </span>
              {formatDuration(elapsed ? position : Math.max(1, duration))}
            </>
          )}
        </span>
      </span>
      {shownRate && onCycleRate ? (
        <Button
          type="button"
          variant="outline"
          size="sm"
          aria-label={t("audio.speed", { rate: shownRate })}
          onClick={onCycleRate}
        >
          {shownRate}
        </Button>
      ) : null}
    </div>
  )
}

/** Áudio com elemento próprio e os controles do projeto, sem `controls` nativos (UI-05). */
export function LocalAudioPlayer({
  src,
  label,
  autoPlay,
  durationMs,
  className,
}: {
  src: string
  /** Nome acessível do tocador. */
  label: string
  autoPlay?: boolean
  /** Duração já conhecida (medida na gravação), mostrada antes de o elemento carregar (AU-02). */
  durationMs?: number | null
  className?: string
}) {
  const audio = useRef<HTMLAudioElement>(null)
  const [state, actions] = useMediaElement(audio)
  return (
    <div role="group" aria-label={label} className={className}>
      <audio ref={audio} src={src} preload="metadata" autoPlay={autoPlay} />
      <AudioControls
        playing={state.playing}
        position={state.position}
        duration={state.duration || (durationMs ?? 0) / 1000}
        rate={state.rate}
        onToggle={actions.toggle}
        onSeek={actions.seek}
        onCycleRate={actions.cycleRate}
        className="w-full"
      />
    </div>
  )
}
