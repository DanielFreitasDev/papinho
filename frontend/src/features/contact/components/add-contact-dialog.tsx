import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useTranslation } from "react-i18next"
import { z } from "@/lib/zod"

import { FormFieldError } from "@/components/form-error"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
  InputGroupText,
} from "@/components/ui/input-group"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { usernameSchema } from "@/features/auth"
import { ApiError, errorMessage } from "@/lib/api-client"
import { useAddContact } from "../api"

const schema = z.object({
  username: usernameSchema,
  nickname: z.string().trim().max(64, "validation.displayNameMax"),
})

type Values = z.input<typeof schema>

/** Adicionar contato pelo @usuario, com apelido local opcional (CT-01). */
export function AddContactDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const add = useAddContact()
  const [error, setError] = useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { username: "", nickname: "" },
  })

  const submit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      const parsed = schema.parse(values)
      await add.mutateAsync({
        username: parsed.username,
        nickname: parsed.nickname || undefined,
      })
      toast.add({ title: t("contacts.added"), type: "success" })
      form.reset()
      onOpenChange(false)
    } catch (caught) {
      setError(
        caught instanceof ApiError && caught.status === 404
          ? t("contacts.notFound")
          : errorMessage(caught, t("common.genericError"))
      )
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={submit} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{t("contacts.addTitle")}</DialogTitle>
            <DialogDescription>
              {t("contacts.addDescription")}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            {error ? (
              <Alert variant="destructive" role="alert">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            <Field data-invalid={!!form.formState.errors.username}>
              <FieldLabel htmlFor="add-contact-username">
                {t("auth.username")}
              </FieldLabel>
              <InputGroup>
                <InputGroupAddon>
                  <InputGroupText>@</InputGroupText>
                </InputGroupAddon>
                <InputGroupInput
                  id="add-contact-username"
                  autoCapitalize="none"
                  autoComplete="off"
                  spellCheck={false}
                  aria-invalid={!!form.formState.errors.username}
                  {...form.register("username")}
                />
              </InputGroup>
              <FormFieldError error={form.formState.errors.username} />
            </Field>
            <Field data-invalid={!!form.formState.errors.nickname}>
              <FieldLabel htmlFor="add-contact-nickname">
                {t("contacts.nickname")}
              </FieldLabel>
              <Input
                id="add-contact-nickname"
                autoComplete="off"
                {...form.register("nickname")}
              />
              <FormFieldError error={form.formState.errors.nickname} />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={add.isPending}>
              {add.isPending ? <Spinner data-icon="inline-start" /> : null}
              {t("contacts.add")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
