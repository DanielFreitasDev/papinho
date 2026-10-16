import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Link } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { FormFieldError } from "@/components/form-error"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { errorMessage } from "@/lib/api-client"
import { useRequestPasswordReset } from "../api"
import { forgotPasswordSchema, type ForgotPasswordValues } from "../schemas"

/** Pede o link de redefinição por e-mail (ID-05). A confirmação é a mesma exista ou não a conta. */
export function ForgotPasswordForm() {
  const { t } = useTranslation()
  const request = useRequestPasswordReset()
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const form = useForm<ForgotPasswordValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { username: "" },
  })

  const submit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      await request.mutateAsync(values.username)
      setSent(true)
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
    }
  })

  return (
    <form onSubmit={submit} noValidate>
      <FieldGroup>
        {sent ? (
          <Alert role="status">
            <AlertTitle>{t("auth.resetSentTitle")}</AlertTitle>
            <AlertDescription>
              {t("auth.resetSentDescription")}
            </AlertDescription>
          </Alert>
        ) : null}
        {error ? (
          <Alert variant="destructive" role="alert">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}
        <Field data-invalid={!!form.formState.errors.username}>
          <FieldLabel htmlFor="forgot-username">
            {t("auth.username")}
          </FieldLabel>
          <Input
            id="forgot-username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            aria-invalid={!!form.formState.errors.username}
            {...form.register("username")}
          />
          <FormFieldError error={form.formState.errors.username} />
        </Field>
        <Field>
          <Button type="submit" size="lg" disabled={request.isPending}>
            {request.isPending ? <Spinner data-icon="inline-start" /> : null}
            {t("auth.sendResetLink")}
          </Button>
          <Link
            to="/recover"
            className="text-center text-sm underline-offset-4 hover:underline"
          >
            {t("auth.useRecoveryCode")}
          </Link>
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
