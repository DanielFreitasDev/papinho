import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { PencilIcon, XIcon } from "lucide-react"

import { UserAvatar } from "@/components/user-avatar"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { formatDateTime } from "@/lib/format"
import { groupQueries } from "../groups"
import type { BroadcastInfo, Conversation } from "../types"
import { BroadcastDialog } from "./broadcast-dialog"
import { ConversationAvatar } from "./conversation-avatar"

/** Painel da lista de transmissão (GR-03, GR-04): destinatários, validade, timer e editar. */
export function BroadcastDetails({
  conversation,
  broadcast,
  onClose,
}: {
  conversation: Conversation
  broadcast: BroadcastInfo
  onClose: () => void
}) {
  const { t } = useTranslation()
  const recipients = useQuery(groupQueries.recipients(conversation.id))
  const [editing, setEditing] = useState(false)
  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex min-h-14 items-center gap-2 border-b px-2">
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("common.close")}
          onClick={onClose}
        >
          <XIcon />
        </Button>
        <h2 className="text-base font-semibold">{t("broadcast.info")}</h2>
      </div>
      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-4">
        <div className="flex flex-col items-center gap-2 text-center">
          <ConversationAvatar conversation={conversation} className="size-28" />
          <p className="text-lg font-semibold break-words">{broadcast.name}</p>
          <p className="text-sm text-muted-foreground">
            {t("broadcast.recipientCount", { count: broadcast.recipientCount })}
          </p>
          <p className="text-sm text-muted-foreground">
            {t("broadcast.onlyContacts")}
          </p>
          {broadcast.expiresAt ? (
            <p className="text-sm">
              {broadcast.expired
                ? t("broadcast.expired")
                : t("broadcast.expiresOn", {
                    when: formatDateTime(broadcast.expiresAt),
                  })}
            </p>
          ) : null}
          {broadcast.messageTtlSeconds ? (
            <p className="text-sm">
              {t("broadcast.timerOn", {
                timer: t(`chat.timer.${broadcast.messageTtlSeconds}`),
              })}
            </p>
          ) : null}
          <Button
            variant="ghost"
            size="sm"
            disabled={!recipients.data}
            onClick={() => setEditing(true)}
          >
            <PencilIcon data-icon="inline-start" />
            {t("broadcast.edit")}
          </Button>
        </div>
        <Separator />
        {recipients.isPending ? (
          <Skeleton className="h-10 w-full" />
        ) : (
          <ul aria-label={t("broadcast.recipients")} className="flex flex-col">
            {recipients.data?.map((person) => (
              <li
                key={person.userId}
                className="flex min-h-12 items-center gap-3 px-1"
              >
                <UserAvatar
                  userId={person.userId}
                  name={person.label}
                  hasPhoto={person.hasPhoto}
                  version={person.photoVersion}
                  className="size-9"
                />
                <span className="flex min-w-0 flex-col">
                  <span className="truncate text-sm">{person.label}</span>
                  <span className="truncate text-xs text-muted-foreground">
                    @{person.username}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
      {editing && recipients.data ? (
        <BroadcastDialog
          open
          onOpenChange={setEditing}
          existing={{
            id: conversation.id,
            info: broadcast,
            recipientIds: recipients.data.map((person) => person.userId),
          }}
        />
      ) : null}
    </div>
  )
}
