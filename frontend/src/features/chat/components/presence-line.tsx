import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { formatWhen } from "@/lib/format"
import { chatQueries } from "../api"
import { useActivity } from "../activity"
import type { Conversation } from "../types"

/**
 * Linha abaixo do nome: "digitando…", "gravando áudio…", "online" ou "visto por último" (MS-03, RT-03). Antes
 * de a outra pessoa aceitar a conversa, o servidor não entrega a presença dela (CT-02).
 */
export function PresenceLine({ conversation }: { conversation: Conversation }) {
  const { t } = useTranslation()
  const activity = useActivity(conversation.id)
  const presence = useQuery({
    ...chatQueries.presence(conversation.peer.userId),
    enabled: conversation.peer.active,
  })
  let text = ""
  if (!conversation.peer.active) {
    text = t("chat.deletedAccount")
  } else if (activity) {
    text = t(
      activity.activity === "recording" ? "chat.recording" : "chat.typing"
    )
  } else if (presence.data?.online) {
    text = t("chat.online")
  } else if (presence.data?.lastSeenAt) {
    text = t("chat.lastSeen", { when: formatWhen(presence.data.lastSeenAt) })
  }
  return (
    <span
      className="block truncate text-xs font-normal text-muted-foreground"
      aria-live="polite"
    >
      {text}
    </span>
  )
}
