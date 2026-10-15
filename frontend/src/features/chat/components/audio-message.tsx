import { useTranslation } from "react-i18next"
import { PauseIcon, PlayIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  formatDuration,
  formatRate,
  player,
  usePlayer,
  type Track,
} from "@/features/media"

/**
 * Áudio na conversa (AU-02): tocar/pausar no tocador global, barra de posição e velocidade 1×, 1,5× e 2×. A
 * fila são os áudios da conversa em ordem, para tocar os seguintes em sequência.
 */
export function AudioMessage({
  track,
  queue,
}: {
  track: Track
  queue: Track[]
}) {
  const { t, i18n } = useTranslation()
  const state = usePlayer()
  const active = state.current?.mediaId === track.mediaId
  const playing = active && state.playing
  const position = active ? state.position : 0
  const duration = active ? state.duration : 0
  const rate = formatRate(state.rate, i18n.language)
  return (
    <div
      className="flex w-64 max-w-full items-center gap-2"
      onClick={(event) => event.stopPropagation()}
    >
      <Button
        type="button"
        variant="ghost"
        size="icon"
        aria-label={playing ? t("audio.pause") : t("audio.play")}
        aria-pressed={playing}
        onClick={() => player.toggle(track, queue)}
      >
        {playing ? <PauseIcon /> : <PlayIcon />}
      </Button>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <input
          type="range"
          min={0}
          max={duration || 1}
          step={0.1}
          value={position}
          disabled={!active}
          aria-label={t("audio.position")}
          aria-valuetext={formatDuration(position)}
          onChange={(event) => player.seek(Number(event.target.value))}
          className="h-1.5 w-full accent-primary"
        />
        <span className="text-xs tabular-nums opacity-80">
          {formatDuration(position)}
          {duration ? ` / ${formatDuration(duration)}` : ""}
        </span>
      </span>
      {active ? (
        <Button
          type="button"
          variant="outline"
          size="sm"
          aria-label={t("audio.speed", { rate })}
          onClick={() => player.cycleRate()}
        >
          {rate}
        </Button>
      ) : null}
    </div>
  )
}
