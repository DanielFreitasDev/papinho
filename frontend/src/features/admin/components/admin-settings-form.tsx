import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import type { RegistrationMode } from "@/features/auth"
import { errorMessage } from "@/lib/api-client"
import { adminQueries, useUpdateSettings, type InstanceSettings } from "../api"

const MODES: RegistrationMode[] = ["INVITE_ONLY", "OPEN", "CLOSED"]

/** Configurações da instância (AD-01, AD-02). */
export function AdminSettingsForm() {
  const { t } = useTranslation()
  const settings = useQuery(adminQueries.settings())
  if (!settings.data) {
    return (
      <Skeleton
        role="status"
        className="h-64 w-full"
        aria-label={t("common.loading")}
      />
    )
  }
  return <SettingsForm initial={settings.data} />
}

function SettingsForm({ initial }: { initial: InstanceSettings }) {
  const { t } = useTranslation()
  const update = useUpdateSettings()
  const [values, setValues] = useState(initial)
  const [error, setError] = useState<string | null>(null)
  const modes = MODES.map((mode) => ({
    value: mode,
    label: t(`admin.mode${mode}`),
  }))

  function number(
    key: keyof InstanceSettings,
    label: string,
    min: number,
    max: number
  ) {
    return (
      <Field>
        <FieldLabel htmlFor={`admin-${key}`}>{label}</FieldLabel>
        <Input
          id={`admin-${key}`}
          type="number"
          inputMode="numeric"
          min={min}
          max={max}
          value={String(values[key])}
          onChange={(event) =>
            setValues({ ...values, [key]: Number(event.target.value) })
          }
        />
      </Field>
    )
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await update.mutateAsync(values)
      toast.add({ title: t("common.saved") })
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
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
        <Field>
          <FieldLabel htmlFor="admin-mode">
            {t("admin.registrationMode")}
          </FieldLabel>
          <Select
            items={modes}
            value={values.registrationMode}
            onValueChange={(value) =>
              setValues({
                ...values,
                registrationMode: value as RegistrationMode,
              })
            }
          >
            <SelectTrigger id="admin-mode" className="w-full sm:w-64">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {modes.map((mode) => (
                  <SelectItem key={mode.value} value={mode.value}>
                    {mode.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        {number(
          "usernameChangeCooldownDays",
          t("admin.usernameCooldown"),
          0,
          365
        )}
        {number("messageEditWindowMinutes", t("admin.editWindow"), 0, 10080)}
        {number("messageDeleteWindowHours", t("admin.deleteWindow"), 0, 8760)}
        {number("userQuotaMegabytes", t("admin.quota"), 1, 10485760)}
        <Field orientation="horizontal">
          <Switch
            id="admin-breached"
            checked={values.breachedPasswordCheck}
            onCheckedChange={(checked) =>
              setValues({ ...values, breachedPasswordCheck: checked })
            }
          />
          <FieldLabel htmlFor="admin-breached">
            {t("admin.breachedCheck")}
          </FieldLabel>
        </Field>
        <Field orientation="horizontal">
          <Button type="submit" disabled={update.isPending}>
            {update.isPending ? <Spinner data-icon="inline-start" /> : null}
            {t("common.save")}
          </Button>
        </Field>
      </FieldGroup>
    </form>
  )
}
