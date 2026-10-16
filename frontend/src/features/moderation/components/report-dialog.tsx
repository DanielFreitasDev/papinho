import { useId, useState } from "react"
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
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { useReport } from "../api"

/** Denunciar (CT-04): as 5 últimas mensagens da conversa vão para a moderação, com opção de bloquear. */
export function ReportDialog({
  open,
  onOpenChange,
  conversationId,
  name,
  alreadyBlocked,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  conversationId: string
  name: string
  alreadyBlocked: boolean
}) {
  const { t } = useTranslation()
  const report = useReport()
  const [reason, setReason] = useState("")
  const [block, setBlock] = useState(!alreadyBlocked)
  const reasonId = useId()
  const blockId = useId()

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            report.mutate(
              { conversationId, reason, block: block && !alreadyBlocked },
              {
                onSuccess: () => {
                  toast.add({ title: t("moderation.reported") })
                  setReason("")
                  onOpenChange(false)
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
          <DialogHeader>
            <DialogTitle>{t("moderation.reportTitle", { name })}</DialogTitle>
            <DialogDescription>
              {t("moderation.reportDescription")}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor={reasonId}>
                {t("moderation.reason")}
              </FieldLabel>
              <Textarea
                id={reasonId}
                maxLength={500}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
              />
            </Field>
            {!alreadyBlocked ? (
              <Field orientation="horizontal">
                <Checkbox
                  id={blockId}
                  checked={block}
                  onCheckedChange={(checked) => setBlock(checked)}
                />
                <FieldLabel htmlFor={blockId}>
                  {t("moderation.alsoBlock", { name })}
                </FieldLabel>
              </Field>
            ) : null}
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              {t("common.cancel")}
            </Button>
            <Button
              type="submit"
              variant="destructive"
              disabled={report.isPending}
            >
              {report.isPending ? <Spinner data-icon="inline-start" /> : null}
              {t("moderation.report")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
