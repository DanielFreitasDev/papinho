import { AudioControls, player, usePlayer, type Track } from "@/features/media"
import { TranscriptView } from "@/features/transcription"

/**
 * Áudio na conversa (AU-02): tocar/pausar no tocador global, barra de posição e velocidade 1×, 1,5× e 2×. A
 * fila são os áudios da conversa em ordem, para tocar os seguintes em sequência. A duração vem do arquivo
 * (lida pelo servidor) e aparece antes do primeiro play; mensagens antigas sem ela usam a do tocador.
 */
export function AudioMessage({
  track,
  queue,
  received,
}: {
  track: Track
  queue: Track[]
  /** Recebido (não enviado por mim): pode ser transcrito automaticamente. */
  received: boolean
}) {
  const state = usePlayer()
  const active = state.current?.mediaId === track.mediaId
  const known = (track.durationMs ?? 0) / 1000
  return (
    <div className="flex flex-col gap-1">
      <div onClick={(event) => event.stopPropagation()}>
        <AudioControls
          playing={active && state.playing}
          position={active ? state.position : 0}
          duration={active && state.duration > 0 ? state.duration : known}
          rate={state.rate}
          disabled={!active}
          onToggle={() => player.toggle(track, queue)}
          onSeek={(seconds) => player.seek(seconds)}
          onCycleRate={active ? () => player.cycleRate() : undefined}
        />
      </div>
      <TranscriptView mediaId={track.mediaId} received={received} />
    </div>
  )
}
