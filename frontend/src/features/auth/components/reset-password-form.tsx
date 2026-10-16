import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Link } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { FormFieldError } from "@/components/form-error"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { errorMessage } from "@/lib/api-client"
import { useConfirmPasswordReset } from "../api"
import { resetPasswordSchema, type ResetPasswordValues } from "../schemas"

/** Nova senha pelo link do e-mail (ID-05). */
export function ResetPasswordForm({
  token,
  onDone,
}: {
  token: string
  onDone: () => void
}) {
  const { t } = useTranslation()
  const confirm = useConfirmPasswordReset()
  const [error, setError] = useState<string | null>(null)
  const form = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  })

  const submit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      await confirm.mutateAsync({ token, newPassword: values.newPassword })
      onDone()
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
    }
  })

  return (
    <form onSubmit={submit} noValidate>
      <FieldGroup>
        {error ? (
          <Alert variant="destructive" role="alert">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}
        <Field data-invalid={!!form.formState.errors.newPassword}>
          <FieldLabel htmlFor="reset-password">
            {t("auth.newPassword")}
          </FieldLabel>
          <Input
            id="reset-password"
            type="password"
            autoComplete="new-password"
            aria-invalid={!!form.formState.errors.newPassword}
            {...form.register("newPassword")}
          />
          <FieldDescription>{t("auth.passwordHint")}</FieldDescription>
          <FormFieldError error={form.formState.errors.newPassword} />
        </Field>
        <Field data-invalid={!!form.formState.errors.confirmPassword}>
          <FieldLabel htmlFor="reset-confirm">
            {t("auth.confirmPassword")}
          </FieldLabel>
          <Input
            id="reset-confirm"
            type="password"
            autoComplete="new-password"
            aria-invalid={!!form.formState.errors.confirmPassword}
            {...form.register("confirmPassword")}
          />
          <FormFieldError error={form.formState.errors.confirmPassword} />
        </Field>
        <Field>
          <Button type="submit" size="lg" disabled={confirm.isPending}>
            {confirm.isPending ? <Spinner data-icon="inline-start" /> : null}
            {t("auth.recover")}
          </Button>
          <Link
            to="/login"
            className="text-center text-sm underline-offset-4 hover:underline"
          >
            {t("auth.backToLogin")}
          </Link>
        </Field>
      </FieldGroup>
    </form>
  )
}
