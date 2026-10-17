import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Link } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { FormFieldError } from "@/components/form-error"
import { PasswordInput } from "@/components/password-input"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { errorMessage } from "@/lib/api-client"
import { useRecover } from "../api"
import { recoverSchema, type RecoverValues } from "../schemas"

/** Recuperação com código de uso único (ID-05). */
export function RecoverForm({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation()
  const recover = useRecover()
  const [error, setError] = useState<string | null>(null)
  const form = useForm<RecoverValues>({
    resolver: zodResolver(recoverSchema),
    defaultValues: { username: "", recoveryCode: "", newPassword: "" },
  })

  const submit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      await recover.mutateAsync(values)
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
        <Field data-invalid={!!form.formState.errors.username}>
          <FieldLabel htmlFor="recover-username">
            {t("auth.username")}
          </FieldLabel>
          <Input
            id="recover-username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            aria-invalid={!!form.formState.errors.username}
            {...form.register("username")}
          />
          <FormFieldError error={form.formState.errors.username} />
        </Field>
        <Field data-invalid={!!form.formState.errors.recoveryCode}>
          <FieldLabel htmlFor="recover-code">
            {t("auth.recoveryCode")}
          </FieldLabel>
          <Input
            id="recover-code"
            autoComplete="one-time-code"
            autoCapitalize="none"
            spellCheck={false}
            placeholder={t("auth.recoveryCodePlaceholder")}
            aria-invalid={!!form.formState.errors.recoveryCode}
            {...form.register("recoveryCode")}
          />
          <FormFieldError error={form.formState.errors.recoveryCode} />
        </Field>
        <Field data-invalid={!!form.formState.errors.newPassword}>
          <FieldLabel htmlFor="recover-password">
            {t("auth.newPassword")}
          </FieldLabel>
          <PasswordInput
            id="recover-password"
            autoComplete="new-password"
            aria-invalid={!!form.formState.errors.newPassword}
            {...form.register("newPassword")}
          />
          <FormFieldError error={form.formState.errors.newPassword} />
        </Field>
        <Field>
          <Button type="submit" size="lg" disabled={recover.isPending}>
            {recover.isPending ? <Spinner data-icon="inline-start" /> : null}
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
