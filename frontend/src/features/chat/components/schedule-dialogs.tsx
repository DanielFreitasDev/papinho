import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { BellOffIcon, TriangleAlertIcon } from "lucide-react"

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
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { formatDateTime } from "@/lib/format"
import { chatQueries, useScheduleAction } from "../api"
import { plainText } from "../format/parse"
import { useErrorToast } from "@/hooks/use-error-toast"

/** Daqui a uma hora, arredondado para os próximos 5 minutos (o passo do seletor de hora). */
function defaultSendAt() {
  const step = 5 * 60_000
  return new Date(Math.ceil((Date.now() + 60 * 60_000) / step) * step)
}

/** Escolhe quando enviar o texto do campo (MS-11). */
export function ScheduleDialog({
  conversationId,
  body,
  onClose,
}: {
  conversationId: string
  body: string | null
  onClose: () => void
}) {
  const { t } = useTranslation()
  const action = useScheduleAction(conversationId)
  const [when, setWhen] = useState(() => toLocalDateTime(defaultSendAt()))
  const [silent, setSilent] = useState(false)
  return (
    <Dialog
      open={body !== null}
      onOpenChange={(open) => (open ? null : onClose())}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("chat.scheduleTitle")}</DialogTitle>
          <DialogDescription className="line-clamp-3">
            {plainText(body ?? "")}
          </DialogDescription>
        </DialogHeader>
        <form
          noValidate
          id="schedule-message"
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            action.mutate(
              {
                action: "schedule",
                body: body ?? "",
                sendAt: new Date(when).toISOString(),
                silent,
              },
              {
                onSuccess: () => {
                  toast.add({ title: t("chat.scheduled") })
                  onClose()
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
            <FieldLabel htmlFor="schedule-when">
              {t("chat.scheduleWhen")}
            </FieldLabel>
            <DateTimePicker
              id="schedule-when"
              label={t("chat.scheduleWhen")}
              value={when}
              min={toLocalDateTime(new Date())}
              onChange={setWhen}
            />
          </Field>
          <Field orientation="horizontal">
            <Switch
              id="schedule-silent"
              checked={silent}
              onCheckedChange={setSilent}
            />
            <FieldLabel htmlFor="schedule-silent">
              {t("chat.sendSilently")}
            </FieldLabel>
          </Field>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button
            type="submit"
            form="schedule-message"
            disabled={!when || action.isPending}
          >
            {t("chat.schedule")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Mensagens agendadas desta conversa, com cancelar (MS-11). */
export function ScheduledListDialog({
  conversationId,
  open,
  onOpenChange,
}: {
  conversationId: string
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const scheduled = useQuery({
    ...chatQueries.scheduled(conversationId),
    enabled: open,
  })
  const action = useScheduleAction(conversationId)
  const onError = useErrorToast()
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("chat.scheduledTitle")}</DialogTitle>
          <DialogDescription>
            {t("chat.scheduledDescription")}
          </DialogDescription>
        </DialogHeader>
        <ul className="flex max-h-80 flex-col gap-2 overflow-y-auto">
          {scheduled.data?.map((item) => (
            <li
              key={item.id}
              className="flex items-start gap-3 rounded-md border p-2"
            >
              <span className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="line-clamp-2 text-sm">
                  {plainText(item.body)}
                </span>
                <span className="flex items-center gap-1 text-xs text-muted-foreground">
                  {item.failedAt ? (
                    <>
                      <TriangleAlertIcon
                        aria-hidden="true"
                        className="size-3.5 text-destructive"
                      />
                      {t("chat.scheduleFailed")}
                    </>
                  ) : (
                    formatDateTime(item.sendAt)
                  )}
                  {item.silent ? (
                    <BellOffIcon
                      aria-label={t("chat.silent")}
                      role="img"
                      className="size-3.5"
                    />
                  ) : null}
                </span>
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  action.mutate({ action: "cancel", id: item.id }, { onError })
                }
              >
                {item.failedAt ? t("chat.remove") : t("common.cancel")}
              </Button>
            </li>
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  )
}
