import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { formatWhen } from "@/lib/format"
import { chatQueries } from "../api"
import { useActivity } from "../activity"
import type { Conversation, GroupMember } from "../types"

/**
 * Linha abaixo do nome: "digitando…", "gravando áudio…", "online" ou "visto por último" (MS-03, RT-03). Antes
 * de a outra pessoa aceitar a conversa, o servidor não entrega a presença dela (CT-02).
 */
export function PresenceLine({
  conversation,
  members,
}: {
  conversation: Conversation
  /** Membros do grupo: a linha mostra quem digita ou a lista de nomes. */
  members?: GroupMember[]
}) {
  const { t } = useTranslation()
  const activity = useActivity(conversation.id)
  const peer = conversation.kind === "DIRECT" ? conversation.peer : null
  const presence = useQuery({
    ...chatQueries.presence(peer?.userId ?? ""),
    enabled: peer?.active ?? false,
  })
  let text = ""
  if (conversation.group) {
    const who = members?.find((member) => member.userId === activity?.userId)
    text = activity
      ? t(
          activity.activity === "recording"
            ? "chat.memberRecording"
            : "chat.memberTyping",
          { name: who?.label ?? "" }
        )
      : (members ?? [])
          .map((member) => (member.me ? t("chat.you") : member.label))
          .join(", ")
  } else if (conversation.broadcast) {
    text = conversation.broadcast.expired
      ? t("broadcast.expiredShort")
      : t("broadcast.recipientCount", {
          count: conversation.broadcast.recipientCount,
        })
  } else if (conversation.kind === "SELF") {
    text = t("chat.savedMessagesHint")
  } else if (peer && !peer.active) {
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
