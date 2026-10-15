import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "@tanstack/react-router"
import { LogOutIcon } from "lucide-react"
import { useTranslation } from "react-i18next"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
  InputGroupText,
} from "@/components/ui/input-group"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { authQueries, useLogout, usernameSchema } from "@/features/auth"
import { errorMessage } from "@/lib/api-client"
import { formatDate } from "@/lib/format"
import { useChangeUsername } from "../api"
import { SettingsSection } from "./settings-section"

/** Troca do @usuario com cooldown e reserva do nome antigo (ID-03). */
export function AccountSettings() {
  const { t } = useTranslation()
  const me = useQuery(authQueries.me())
  const change = useChangeUsername()
  const logout = useLogout()
  const navigate = useNavigate()
  const [value, setValue] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const account = me.data?.account
  if (!account) {
    return null
  }
  const current = value ?? account.username
  const locked =
    account.nextUsernameChangeAt !== null &&
    new Date(account.nextUsernameChangeAt) > new Date()

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    const parsed = usernameSchema.safeParse(current)
    if (!parsed.success) {
      setError(t("validation.username"))
      return
    }
    try {
      await change.mutateAsync(parsed.data)
      setValue(null)
      toast.add({ title: t("account.usernameChanged") })
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <SettingsSection
        title={t("account.changeUsername")}
        description={t("account.usernameReservation")}
      >
        <form onSubmit={submit} noValidate>
          <FieldGroup>
            {error ? (
              <Alert variant="destructive" role="alert">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            <Field data-invalid={!!error}>
              <FieldLabel htmlFor="account-username">
                {t("account.username")}
              </FieldLabel>
              <InputGroup>
                <InputGroupAddon>
                  <InputGroupText>@</InputGroupText>
                </InputGroupAddon>
                <InputGroupInput
                  id="account-username"
                  autoCapitalize="none"
                  spellCheck={false}
                  value={current}
                  disabled={locked}
                  onChange={(event) => setValue(event.target.value)}
                />
              </InputGroup>
              {locked && account.nextUsernameChangeAt ? (
                <FieldDescription>
                  {t("account.usernameCooldown", {
                    date: formatDate(account.nextUsernameChangeAt),
                  })}
                </FieldDescription>
              ) : null}
            </Field>
            <Field orientation="horizontal">
              <Button
                type="submit"
                disabled={
                  locked || change.isPending || current === account.username
                }
              >
                {change.isPending ? <Spinner data-icon="inline-start" /> : null}
                {t("common.save")}
              </Button>
            </Field>
          </FieldGroup>
        </form>
      </SettingsSection>
      <SettingsSection title={t("auth.logout")}>
        <Button
          variant="outline"
          onClick={async () => {
            await logout.mutateAsync().catch(() => undefined)
            await navigate({ to: "/login" })
          }}
        >
          <LogOutIcon data-icon="inline-start" />
          {t("auth.logout")}
        </Button>
      </SettingsSection>
    </div>
  )
}
