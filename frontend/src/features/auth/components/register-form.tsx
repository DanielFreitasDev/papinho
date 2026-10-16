import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Link } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"
import { CheckIcon, XIcon } from "lucide-react"

import { FormFieldError } from "@/components/form-error"
import { PasswordInput } from "@/components/password-input"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
  InputGroupText,
} from "@/components/ui/input-group"
import { Spinner } from "@/components/ui/spinner"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { errorMessage } from "@/lib/api-client"
import { checkUsername, useRegister } from "../api"
import {
  normalizeUsername,
  registerSchema,
  usernameSchema,
  type RegisterValues,
} from "../schemas"
import { RecoveryCodesDialog } from "./recovery-codes-dialog"

/**
 * Cadastro (ID-01): nome de exibição, @usuario com checagem de disponibilidade e senha. Depois do cadastro
 * mostra os códigos de recuperação e só então segue para o app.
 */
export function RegisterForm({
  inviteToken,
  firstAdmin,
  onDone,
}: {
  inviteToken?: string
  firstAdmin: boolean
  onDone: () => void
}) {
  const { t } = useTranslation()
  const register = useRegister()
  const [error, setError] = useState<string | null>(null)
  const [codes, setCodes] = useState<string[] | null>(null)
  const form = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      displayName: "",
      username: "",
      password: "",
      confirmPassword: "",
    },
  })
  const typedUsername = useDebouncedValue(
    useWatch({ control: form.control, name: "username" })
  )

  const parsedUsername = usernameSchema.safeParse(typedUsername)
  const candidate = parsedUsername.success ? parsedUsername.data : null
  const availabilityQuery = useQuery({
    queryKey: ["username-availability", candidate],
    queryFn: () => checkUsername(candidate ?? ""),
    enabled: candidate !== null,
    staleTime: 10_000,
  })
  const availability =
    candidate === null || availabilityQuery.data === undefined
      ? "unknown"
      : availabilityQuery.data
        ? "available"
        : "taken"

  const submit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      const response = await register.mutateAsync({
        displayName: values.displayName,
        username: normalizeUsername(values.username),
        password: values.password,
        inviteToken,
      })
      setCodes(response.recoveryCodes)
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
    }
  })

  return (
    <>
      <form onSubmit={submit} noValidate>
        <FieldGroup>
          {firstAdmin ? (
            <Alert>
              <AlertTitle>{t("auth.firstAdminTitle")}</AlertTitle>
              <AlertDescription>
                {t("auth.firstAdminDescription")}
              </AlertDescription>
            </Alert>
          ) : null}
          {error ? (
            <Alert variant="destructive" role="alert">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
          <Field data-invalid={!!form.formState.errors.displayName}>
            <FieldLabel htmlFor="register-name">
              {t("auth.displayName")}
            </FieldLabel>
            <Input
              id="register-name"
              autoComplete="name"
              enterKeyHint="next"
              aria-invalid={!!form.formState.errors.displayName}
              {...form.register("displayName")}
            />
            <FieldDescription>{t("auth.displayNameHint")}</FieldDescription>
            <FormFieldError error={form.formState.errors.displayName} />
          </Field>
          <Field
            data-invalid={
              !!form.formState.errors.username || availability === "taken"
            }
          >
            <FieldLabel htmlFor="register-username">
              {t("auth.username")}
            </FieldLabel>
            <InputGroup>
              <InputGroupAddon>
                <InputGroupText>@</InputGroupText>
              </InputGroupAddon>
              <InputGroupInput
                id="register-username"
                autoComplete="username"
                autoCapitalize="none"
                spellCheck={false}
                enterKeyHint="next"
                aria-invalid={
                  !!form.formState.errors.username || availability === "taken"
                }
                aria-describedby="register-username-status"
                {...form.register("username")}
              />
              <InputGroupAddon align="inline-end">
                <InputGroupText
                  id="register-username-status"
                  aria-live="polite"
                >
                  {availability === "available" ? (
                    <>
                      <CheckIcon aria-hidden="true" />
                      <span className="sr-only sm:not-sr-only">
                        {t("auth.usernameAvailable")}
                      </span>
                    </>
                  ) : availability === "taken" ? (
                    <>
                      <XIcon aria-hidden="true" />
                      <span className="sr-only sm:not-sr-only">
                        {t("auth.usernameTaken")}
                      </span>
                    </>
                  ) : null}
                </InputGroupText>
              </InputGroupAddon>
            </InputGroup>
            <FieldDescription>{t("auth.usernameHint")}</FieldDescription>
            <FormFieldError error={form.formState.errors.username} />
          </Field>
          <Field data-invalid={!!form.formState.errors.password}>
            <FieldLabel htmlFor="register-password">
              {t("auth.password")}
            </FieldLabel>
            <PasswordInput
              id="register-password"
              autoComplete="new-password"
              enterKeyHint="next"
              aria-invalid={!!form.formState.errors.password}
              {...form.register("password")}
            />
            <FieldDescription>{t("auth.passwordHint")}</FieldDescription>
            <FormFieldError error={form.formState.errors.password} />
          </Field>
          <Field data-invalid={!!form.formState.errors.confirmPassword}>
            <FieldLabel htmlFor="register-confirm">
              {t("auth.confirmPassword")}
            </FieldLabel>
            <PasswordInput
              id="register-confirm"
              autoComplete="new-password"
              enterKeyHint="go"
              aria-invalid={!!form.formState.errors.confirmPassword}
              {...form.register("confirmPassword")}
            />
            <FormFieldError error={form.formState.errors.confirmPassword} />
          </Field>
          <Field>
            <Button type="submit" size="lg" disabled={register.isPending}>
              {register.isPending ? <Spinner data-icon="inline-start" /> : null}
              {t("auth.createAccount")}
            </Button>
            <FieldDescription className="text-center">
              {t("auth.haveAccount")} <Link to="/login">{t("auth.login")}</Link>
            </FieldDescription>
          </Field>
        </FieldGroup>
      </form>
      <RecoveryCodesDialog
        codes={codes ?? []}
        open={codes !== null}
        onAcknowledge={onDone}
      />
    </>
  )
}
