import { Link, useRouterState } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"
import { PauseIcon, PlayIcon, XIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { formatDuration, formatRate, player, usePlayer } from "../player"

/**
 * Mini-player global (AU-02): aparece quando o áudio que está tocando é de outra conversa que não a aberta,
 * com tocar/pausar, velocidade, ir para a conversa e fechar.
 */
export function MiniPlayer() {
  const { t, i18n } = useTranslation()
  const state = usePlayer()
  const rate = formatRate(state.rate, i18n.language)
  const pathname = useRouterState({ select: (s) => s.location.pathname })
  const current = state.current
  if (!current || pathname === `/c/${current.conversationId}`) {
    return null
  }
  return (
    <section
      aria-label={t("audio.miniPlayer")}
      className="flex items-center gap-2 border-b bg-muted px-2 py-1 pt-[max(0.25rem,env(safe-area-inset-top))]"
    >
      <Button
        variant="ghost"
        size="icon"
        aria-label={state.playing ? t("audio.pause") : t("audio.play")}
        onClick={() => player.toggle(current, state.queue)}
      >
        {state.playing ? <PauseIcon /> : <PlayIcon />}
      </Button>
      <Link
        to="/c/$conversationId"
        params={{ conversationId: current.conversationId }}
        className="min-w-0 flex-1 truncate text-sm font-medium outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        {current.title}
      </Link>
      <span className="text-xs tabular-nums">
        {formatDuration(state.position)}
      </span>
      <Button
        variant="outline"
        size="sm"
        aria-label={t("audio.speed", { rate })}
        onClick={() => player.cycleRate()}
      >
        {rate}
      </Button>
      <Button
        variant="ghost"
        size="icon"
        aria-label={t("audio.closePlayer")}
        onClick={() => player.stop()}
      >
        <XIcon />
      </Button>
    </section>
  )
}
