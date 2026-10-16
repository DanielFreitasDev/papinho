import { useState } from "react"
import { useTranslation } from "react-i18next"
import { PlusIcon, XIcon } from "lucide-react"

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
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { useCreatePoll } from "../api"

const MAX_OPTIONS = 12

/** Nova enquete (MS-09): pergunta, opções, escolha múltipla, anônima e prazo opcional. */
export function PollDialog({
  conversationId,
  open,
  onOpenChange,
}: {
  conversationId: string
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const create = useCreatePoll(conversationId)
  const [question, setQuestion] = useState("")
  const [options, setOptions] = useState(["", ""])
  const [multiple, setMultiple] = useState(false)
  const [anonymous, setAnonymous] = useState(false)
  const [closesAt, setClosesAt] = useState("")
  const filled = options.map((option) => option.trim()).filter(Boolean)
  const valid =
    question.trim() !== "" &&
    filled.length >= 2 &&
    new Set(filled).size === filled.length

  function close() {
    setQuestion("")
    setOptions(["", ""])
    setMultiple(false)
    setAnonymous(false)
    setClosesAt("")
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? null : close())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("poll.new")}</DialogTitle>
          <DialogDescription>{t("poll.newDescription")}</DialogDescription>
        </DialogHeader>
        <form
          noValidate
          id="new-poll"
          className="flex max-h-[60dvh] flex-col gap-4 overflow-y-auto p-0.5"
          onSubmit={(event) => {
            event.preventDefault()
            if (!valid) {
              return
            }
            create.mutate(
              {
                question: question.trim(),
                options: filled,
                multiple,
                anonymous,
                closesAt: closesAt ? new Date(closesAt).toISOString() : null,
              },
              {
                onSuccess: close,
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
            <FieldLabel htmlFor="poll-question">
              {t("poll.question")}
            </FieldLabel>
            <Input
              id="poll-question"
              required
              maxLength={300}
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
            />
          </Field>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-sm font-medium">
              {t("poll.options")}
            </legend>
            {options.map((option, index) => (
              <div key={index} className="flex items-center gap-2">
                <Input
                  aria-label={t("poll.option", { number: index + 1 })}
                  maxLength={100}
                  value={option}
                  onChange={(event) =>
                    setOptions(
                      options.map((current, i) =>
                        i === index ? event.target.value : current
                      )
                    )
                  }
                />
                {options.length > 2 ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    aria-label={t("poll.removeOption", { number: index + 1 })}
                    onClick={() =>
                      setOptions(options.filter((_, i) => i !== index))
                    }
                  >
                    <XIcon />
                  </Button>
                ) : null}
              </div>
            ))}
            {options.length < MAX_OPTIONS ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="self-start"
                onClick={() => setOptions([...options, ""])}
              >
                <PlusIcon data-icon="inline-start" />
                {t("poll.addOption")}
              </Button>
            ) : null}
          </fieldset>
          <Field orientation="horizontal">
            <Switch
              id="poll-multiple"
              checked={multiple}
              onCheckedChange={setMultiple}
            />
            <FieldLabel htmlFor="poll-multiple">
              {t("poll.multiple")}
            </FieldLabel>
          </Field>
          <Field orientation="horizontal">
            <Switch
              id="poll-anonymous"
              checked={anonymous}
              onCheckedChange={setAnonymous}
            />
            <FieldLabel htmlFor="poll-anonymous">
              {t("poll.anonymous")}
            </FieldLabel>
          </Field>
          <Field>
            <FieldLabel htmlFor="poll-closes">{t("poll.closesAt")}</FieldLabel>
            <DateTimePicker
              id="poll-closes"
              label={t("poll.closesAt")}
              value={closesAt}
              min={toLocalDateTime(new Date())}
              clearable
              onChange={setClosesAt}
            />
          </Field>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={close}>
            {t("common.cancel")}
          </Button>
          <Button
            type="submit"
            form="new-poll"
            disabled={!valid || create.isPending}
          >
            {create.isPending ? <Spinner data-icon="inline-start" /> : null}
            {t("poll.send")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
