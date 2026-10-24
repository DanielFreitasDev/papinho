import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { chatQueries, useForward } from "../api"
import { ConversationAvatar, useConversationLabel } from "./conversation-avatar"

const MAX_TARGETS = 5

/** Encaminhar mensagens para até cinco conversas (MS-04). */
export function ForwardDialog({
  messageIds,
  onClose,
}: {
  messageIds: string[]
  onClose: (sent: boolean) => void
}) {
  const { t } = useTranslation()
  const conversations = useQuery(chatQueries.list(false))
  const forward = useForward()
  const labelOf = useConversationLabel()
  const [targets, setTargets] = useState<string[]>([])
  const open = messageIds.length > 0

  function toggle(id: string) {
    setTargets((current) =>
      current.includes(id)
        ? current.filter((item) => item !== id)
        : current.length < MAX_TARGETS
          ? [...current, id]
          : current
    )
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          setTargets([])
          onClose(false)
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("chat.forwardTitle")}</DialogTitle>
          <DialogDescription>{t("chat.forwardDescription")}</DialogDescription>
        </DialogHeader>
        <fieldset className="flex max-h-80 min-w-0 flex-col gap-1 overflow-y-auto">
          <legend className="sr-only">{t("chat.conversations")}</legend>
          {conversations.data
            ?.filter((conversation) => conversation.canSend)
            .map((conversation) => (
              <label
                key={conversation.id}
                className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md px-2 py-1 hover:bg-muted"
              >
                <Checkbox
                  checked={targets.includes(conversation.id)}
                  disabled={
                    !targets.includes(conversation.id) &&
                    targets.length >= MAX_TARGETS
                  }
                  onCheckedChange={() => toggle(conversation.id)}
                />
                <ConversationAvatar
                  conversation={conversation}
                  className="size-8"
                />
                <span className="truncate text-sm">
                  {labelOf(conversation)}
                </span>
              </label>
            ))}
        </fieldset>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => {
              setTargets([])
              onClose(false)
            }}
          >
            {t("common.cancel")}
          </Button>
          <Button
            disabled={targets.length === 0 || forward.isPending}
            onClick={async () => {
              try {
                await forward.mutateAsync({
                  messageIds,
                  conversationIds: targets,
                })
                toast.add({ title: t("chat.forwardSent"), type: "success" })
                setTargets([])
                onClose(true)
              } catch (error) {
                toast.add({
                  title: errorMessage(error, t("common.genericError")),
                  type: "error",
                })
              }
            }}
          >
            {forward.isPending ? <Spinner data-icon="inline-start" /> : null}
            {t("chat.forward")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
