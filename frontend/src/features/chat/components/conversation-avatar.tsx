import { useCallback } from "react"
import { useTranslation } from "react-i18next"
import { BookmarkIcon, MegaphoneIcon, UsersIcon } from "lucide-react"
import { cn } from "cn"

import { UserAvatar } from "@/components/user-avatar"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import type { Conversation } from "../types"

/** Nome da conversa: o do grupo, "Mensagens salvas" ou como eu vejo a outra pessoa. */
export function useConversationLabel() {
  const { t } = useTranslation()
  return useCallback(
    (conversation: Conversation) =>
      conversation.group
        ? conversation.group.name
        : conversation.broadcast
          ? conversation.broadcast.name
          : conversation.kind === "SELF"
            ? t("chat.savedMessages")
            : (conversation.peer?.label ?? ""),
    [t]
  )
}

/** Avatar da conversa: foto do grupo (só membros abrem), marcador das mensagens salvas ou a pessoa. */
export function ConversationAvatar({
  conversation,
  className,
}: {
  conversation: Conversation
  className?: string
}) {
  if (conversation.group) {
    const photo = conversation.group.photoId
    return (
      <Avatar className={cn("size-10", className)} data-private="photos">
        {photo ? (
          <AvatarImage
            src={`/api/v1/media/${photo}/thumbnail?v=${encodeURIComponent(conversation.group.photoVersion)}`}
            alt=""
          />
        ) : null}
        <AvatarFallback aria-hidden="true">
          <UsersIcon className="size-1/2" />
        </AvatarFallback>
      </Avatar>
    )
  }
  if (conversation.broadcast) {
    return (
      <Avatar className={cn("size-10", className)}>
        <AvatarFallback aria-hidden="true">
          <MegaphoneIcon className="size-1/2" />
        </AvatarFallback>
      </Avatar>
    )
  }
  if (conversation.kind === "SELF") {
    return (
      <Avatar className={cn("size-10", className)}>
        <AvatarFallback
          aria-hidden="true"
          className="bg-primary text-primary-foreground"
        >
          <BookmarkIcon className="size-1/2" />
        </AvatarFallback>
      </Avatar>
    )
  }
  const peer = conversation.peer
  return peer ? (
    <UserAvatar
      userId={peer.userId}
      name={peer.label}
      hasPhoto={peer.hasPhoto}
      version={peer.photoVersion}
      className={className}
      privateItem
    />
  ) : null
}
