import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Link } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"
import { KeyRoundIcon } from "lucide-react"
import { useQueryClient } from "@tanstack/react-query"

import { FormFieldError } from "@/components/form-error"
import { PasswordInput } from "@/components/password-input"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
  FieldSeparator,
} from "@/components/ui/field"
import { OtpInput } from "@/components/otp-input"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { ApiError, errorMessage } from "@/lib/api-client"
import { loginWithPasskey, passkeysSupported } from "@/lib/webauthn"
import { authQueries, useLogin } from "../api"
import { loginSchema, type LoginValues } from "../schemas"

/** Login por senha, com o passo do TOTP quando a conta exige, e por passkey (ID-04, ID-05). */
export function LoginForm({
  onSuccess,
  canRegister,
}: {
  onSuccess: () => void
  canRegister: boolean
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const login = useLogin()
  const [totpRequired, setTotpRequired] = useState(false)
  const [totpCode, setTotpCode] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [passkeyPending, setPasskeyPending] = useState(false)
  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" },
  })

  const submit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      await login.mutateAsync({
        ...values,
        totpCode: totpRequired ? totpCode : undefined,
      })
      onSuccess()
    } catch (caught) {
      if (caught instanceof ApiError && caught.problem.totpRequired === true) {
        setTotpRequired(true)
        return
      }
      setError(errorMessage(caught, t("common.genericError")))
    }
  })

  async function passkeyLogin() {
    setError(null)
    setPasskeyPending(true)
    try {
      await loginWithPasskey()
      // A sessão nasceu fora do useLogin: busca o usuário de novo antes de navegar, senão o guard das
      // rotas autenticadas reaproveitaria o "sem sessão" guardado em cache.
      await queryClient.fetchQuery({ ...authQueries.me(), staleTime: 0 })
      onSuccess()
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? errorMessage(caught, t("auth.passkeyFailed"))
          : t("auth.passkeyFailed")
      )
    } finally {
      setPasskeyPending(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      <FieldGroup>
        {error ? (
          <Alert variant="destructive" role="alert">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}
        <Field data-invalid={!!form.formState.errors.username}>
          <FieldLabel htmlFor="login-username">{t("auth.username")}</FieldLabel>
          <Input
            id="login-username"
            autoComplete="username webauthn"
            autoCapitalize="none"
            spellCheck={false}
            enterKeyHint="next"
            aria-invalid={!!form.formState.errors.username}
            {...form.register("username")}
          />
          <FormFieldError error={form.formState.errors.username} />
        </Field>
        <Field data-invalid={!!form.formState.errors.password}>
          <FieldLabel htmlFor="login-password">{t("auth.password")}</FieldLabel>
          <PasswordInput
            id="login-password"
            autoComplete="current-password"
            enterKeyHint="go"
            aria-invalid={!!form.formState.errors.password}
            {...form.register("password")}
          />
          <FormFieldError error={form.formState.errors.password} />
        </Field>
        {totpRequired ? (
          <Field>
            <FieldLabel htmlFor="login-totp">{t("auth.totpTitle")}</FieldLabel>
            <FieldDescription>{t("auth.totpDescription")}</FieldDescription>
            <OtpInput
              id="login-totp"
              value={totpCode}
              onChange={setTotpCode}
              autoFocus
            />
          </Field>
        ) : null}
        <Field>
          <Button type="submit" size="lg" disabled={login.isPending}>
            {login.isPending ? <Spinner data-icon="inline-start" /> : null}
            {t("auth.login")}
          </Button>
          <Link
            to="/recover"
            className="text-center text-sm underline-offset-4 hover:underline"
          >
            {t("auth.forgotPassword")}
          </Link>
        </Field>
        {passkeysSupported() ? (
          <>
            <FieldSeparator>{t("common.or")}</FieldSeparator>
            <Field>
              <Button
                type="button"
                variant="outline"
                size="lg"
                onClick={passkeyLogin}
                disabled={passkeyPending}
              >
                {passkeyPending ? (
                  <Spinner data-icon="inline-start" />
                ) : (
                  <KeyRoundIcon data-icon="inline-start" />
                )}
                {t("auth.loginWithPasskey")}
              </Button>
            </Field>
          </>
        ) : null}
        {canRegister ? (
          <FieldDescription className="text-center">
            {t("auth.noAccount")}{" "}
            <Link to="/register">{t("auth.createAccount")}</Link>
          </FieldDescription>
        ) : null}
      </FieldGroup>
    </form>
  )
}
