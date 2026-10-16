import { useState } from "react"
import { useNavigate } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { DateTimePicker, toLocalDateTime } from "@/components/date-picker"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { useSaveBroadcast } from "../groups"
import type { BroadcastInfo } from "../types"
import { ContactPicker } from "./contact-picker"
import { DisappearingSelect } from "./disappearing-select"

function toLocalInput(value: string | null) {
  return value ? toLocalDateTime(new Date(value)) : ""
}

/**
 * Nova lista de transmissão ou edição de uma existente (GR-03): nome, contatos e, para listas temporárias
 * (GR-04), a data em que a lista expira e o timer das mensagens enviadas por ela.
 */
export function BroadcastDialog({
  open,
  onOpenChange,
  existing,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Lista a editar, com os destinatários atuais. */
  existing?: { id: string; info: BroadcastInfo; recipientIds: string[] }
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const save = useSaveBroadcast(existing?.id)
  const [name, setName] = useState(existing?.info.name ?? "")
  const [recipients, setRecipients] = useState(existing?.recipientIds ?? [])
  const [expiresAt, setExpiresAt] = useState(
    toLocalInput(existing?.info.expiresAt ?? null)
  )
  const [ttl, setTtl] = useState<number | null>(
    existing?.info.messageTtlSeconds ?? null
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {existing ? t("broadcast.edit") : t("broadcast.new")}
          </DialogTitle>
          <DialogDescription>{t("broadcast.description")}</DialogDescription>
        </DialogHeader>
        <form
          noValidate
          id="broadcast-form"
          className="flex max-h-[60dvh] min-h-0 flex-col gap-4 overflow-y-auto p-0.5"
          onSubmit={(event) => {
            event.preventDefault()
            save.mutate(
              {
                name: name.trim(),
                recipientIds: recipients,
                expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
                messageTtlSeconds: ttl,
              },
              {
                onSuccess: async (conversation) => {
                  onOpenChange(false)
                  if (!existing) {
                    await navigate({
                      to: "/c/$conversationId",
                      params: { conversationId: conversation.id },
                    })
                  }
                },
                onError: (error) =>
                  toast.add({
                    title: errorMessage(error, t("common.genericError")),
                    type: "error",
                  }),
              }
            )
          }}
        >
          <Field>
            <FieldLabel htmlFor="broadcast-name">
              {t("broadcast.name")}
            </FieldLabel>
            <Input
              id="broadcast-name"
              required
              maxLength={100}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <ContactPicker selected={recipients} onChange={setRecipients} />
          <Field>
            <FieldLabel htmlFor="broadcast-expires">
              {t("broadcast.expiresAt")}
            </FieldLabel>
            <DateTimePicker
              id="broadcast-expires"
              label={t("broadcast.expiresAt")}
              value={expiresAt}
              min={toLocalDateTime(new Date())}
              clearable
              onChange={setExpiresAt}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="broadcast-ttl">
              {t("broadcast.messageTimer")}
            </FieldLabel>
            <DisappearingSelect
              id="broadcast-ttl"
              value={ttl}
              onChange={setTtl}
            />
          </Field>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t("common.cancel")}
          </Button>
          <Button
            type="submit"
            form="broadcast-form"
            disabled={
              name.trim() === "" || recipients.length === 0 || save.isPending
            }
          >
            {save.isPending ? <Spinner data-icon="inline-start" /> : null}
            {existing ? t("common.save") : t("broadcast.create")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
