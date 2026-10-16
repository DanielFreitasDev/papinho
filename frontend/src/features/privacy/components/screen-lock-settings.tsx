import { useState, type FormEvent } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { LockIcon } from "lucide-react"

import { PasswordInput } from "@/components/password-input"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import { SettingsSection } from "@/features/account"
import { errorMessage } from "@/lib/api-client"
import { passkeysSupported } from "@/lib/webauthn"
import { privacyQueries, useRemoveScreenLock, useSaveScreenLock } from "../api"
import { lockScreenNow, shortcutFromEvent } from "../screen-lock"
import type { ScreenLock, ScreenLockKind } from "../types"

const IDLE_OPTIONS = [null, 1, 5, 15, 30, 60] as const

type Triggers = Pick<
  ScreenLock,
  "idleMinutes" | "lockOnHidden" | "shortcut" | "allowPasskey"
>

/** Bloqueio de tela (PV-02): segredo próprio e gatilhos, tudo desligado por padrão. */
export function ScreenLockSettings() {
  const { t } = useTranslation()
  const lock = useQuery(privacyQueries.screenLock())
  return (
    <SettingsSection
      title={t("privacy.screenLock.title")}
      description={t("privacy.screenLock.description")}
    >
      {lock.isPending ? (
        <Skeleton className="h-24 w-full" />
      ) : lock.data?.enabled ? (
        <EnabledLock lock={lock.data} />
      ) : (
        <NewLock />
      )}
    </SettingsSection>
  )
}

function NewLock() {
  const { t } = useTranslation()
  const save = useSaveScreenLock()
  const [kind, setKind] = useState<ScreenLockKind>("PIN")
  const [secret, setSecret] = useState("")
  const [confirm, setConfirm] = useState("")
  const [triggers, setTriggers] = useState<Triggers>({
    idleMinutes: null,
    lockOnHidden: false,
    shortcut: "Alt+Shift+L",
    allowPasskey: false,
  })
  const [error, setError] = useState<string | null>(null)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (secret !== confirm) {
      setError(t("privacy.screenLock.mismatch"))
      return
    }
    setError(null)
    save.mutate(
      { kind, secret, ...triggers },
      {
        onSuccess: () => {
          toast.add({ title: t("privacy.screenLock.enabled") })
          setSecret("")
          setConfirm("")
        },
        onError: (failure) =>
          setError(errorMessage(failure, t("common.genericError"))),
      }
    )
  }

  const kinds = [
    { value: "PIN", label: t("privacy.screenLock.kind.PIN") },
    { value: "PASSWORD", label: t("privacy.screenLock.kind.PASSWORD") },
  ]
  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="screen-lock-kind">
            {t("privacy.screenLock.kindLabel")}
          </FieldLabel>
          <Select
            items={kinds}
            value={kind}
            onValueChange={(value) =>
              setKind((value ?? "PIN") as ScreenLockKind)
            }
          >
            <SelectTrigger id="screen-lock-kind" className="w-full sm:w-64">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {kinds.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        <SecretInput
          id="screen-lock-new"
          kind={kind}
          label={
            kind === "PIN"
              ? t("privacy.screenLock.newPin")
              : t("privacy.screenLock.newPassword")
          }
          value={secret}
          onChange={setSecret}
        />
        <SecretInput
          id="screen-lock-confirm"
          kind={kind}
          label={t("privacy.screenLock.confirm")}
          value={confirm}
          onChange={setConfirm}
        />
        <TriggerFields value={triggers} onChange={setTriggers} />
      </FieldGroup>
      {error ? (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}
      <Button
        type="submit"
        className="self-start"
        disabled={save.isPending || !secret}
      >
        {t("privacy.screenLock.turnOn")}
      </Button>
    </form>
  )
}

function EnabledLock({ lock }: { lock: ScreenLock }) {
  const { t } = useTranslation()
  const save = useSaveScreenLock()
  const remove = useRemoveScreenLock()
  const [triggers, setTriggers] = useState<Triggers>({
    idleMinutes: lock.idleMinutes,
    lockOnHidden: lock.lockOnHidden,
    shortcut: lock.shortcut,
    allowPasskey: lock.allowPasskey,
  })
  const [current, setCurrent] = useState("")
  const [error, setError] = useState<string | null>(null)
  const kind = lock.kind ?? "PIN"

  return (
    <div className="flex flex-col gap-4">
      <FieldGroup>
        <TriggerFields value={triggers} onChange={setTriggers} />
      </FieldGroup>
      <div className="flex flex-wrap gap-2">
        <Button
          disabled={save.isPending}
          onClick={() =>
            save.mutate(triggers, {
              onSuccess: () => toast.add({ title: t("common.saved") }),
              onError: (failure) =>
                toast.add({
                  title: errorMessage(failure, t("common.genericError")),
                  type: "error",
                }),
            })
          }
        >
          {t("common.save")}
        </Button>
        <Button variant="outline" onClick={() => lockScreenNow()}>
          <LockIcon />
          {t("privacy.screenLock.lockNow")}
        </Button>
      </div>
      <form
        className="flex flex-col gap-3 border-t pt-4"
        onSubmit={(event) => {
          event.preventDefault()
          remove.mutate(current, {
            onSuccess: () => {
              toast.add({ title: t("privacy.screenLock.disabled") })
            },
            onError: (failure) =>
              setError(errorMessage(failure, t("common.genericError"))),
          })
        }}
      >
        <SecretInput
          id="screen-lock-current"
          kind={kind}
          label={t("privacy.screenLock.currentToTurnOff")}
          value={current}
          onChange={setCurrent}
        />
        {error ? (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}
        <Button
          type="submit"
          variant="destructive"
          className="self-start"
          disabled={remove.isPending || !current}
        >
          {t("privacy.screenLock.turnOff")}
        </Button>
      </form>
    </div>
  )
}

function SecretInput({
  id,
  kind,
  label,
  value,
  onChange,
}: {
  id: string
  kind: ScreenLockKind
  label: string
  value: string
  onChange: (value: string) => void
}) {
  const { t } = useTranslation()
  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <PasswordInput
        id={id}
        autoComplete="new-password"
        inputMode={kind === "PIN" ? "numeric" : undefined}
        className="sm:w-64"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
      <FieldDescription>
        {kind === "PIN"
          ? t("privacy.screenLock.pinHint")
          : t("privacy.screenLock.passwordHint")}
      </FieldDescription>
    </Field>
  )
}

function TriggerFields({
  value,
  onChange,
}: {
  value: Triggers
  onChange: (value: Triggers) => void
}) {
  const { t } = useTranslation()
  const [recording, setRecording] = useState(false)
  const idle = IDLE_OPTIONS.map((minutes) => ({
    value: minutes === null ? "off" : String(minutes),
    label:
      minutes === null
        ? t("privacy.screenLock.idleOff")
        : t("privacy.screenLock.idleMinutes", { count: minutes }),
  }))
  return (
    <>
      <Field>
        <FieldLabel htmlFor="screen-lock-idle">
          {t("privacy.screenLock.idle")}
        </FieldLabel>
        <Select
          items={idle}
          value={value.idleMinutes === null ? "off" : String(value.idleMinutes)}
          onValueChange={(next) =>
            onChange({
              ...value,
              idleMinutes:
                next === "off" || next === null ? null : Number(next),
            })
          }
        >
          <SelectTrigger id="screen-lock-idle" className="w-full sm:w-64">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {idle.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </Field>
      <Field orientation="horizontal">
        <FieldContent>
          <FieldLabel htmlFor="screen-lock-hidden">
            {t("privacy.screenLock.onHidden")}
          </FieldLabel>
        </FieldContent>
        <Switch
          id="screen-lock-hidden"
          checked={value.lockOnHidden}
          onCheckedChange={(lockOnHidden) =>
            onChange({ ...value, lockOnHidden })
          }
        />
      </Field>
      <Field>
        <FieldLabel htmlFor="screen-lock-shortcut">
          {t("privacy.screenLock.shortcut")}
        </FieldLabel>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            id="screen-lock-shortcut"
            type="button"
            variant="outline"
            aria-describedby="screen-lock-shortcut-hint"
            onClick={() => setRecording(true)}
            onBlur={() => setRecording(false)}
            onKeyDown={(event) => {
              if (!recording) {
                return
              }
              if (event.key === "Escape") {
                setRecording(false)
                return
              }
              const shortcut = shortcutFromEvent(event.nativeEvent)
              if (shortcut) {
                event.preventDefault()
                onChange({ ...value, shortcut })
                setRecording(false)
              }
            }}
          >
            {recording
              ? t("privacy.screenLock.pressKeys")
              : (value.shortcut ?? t("privacy.screenLock.noShortcut"))}
          </Button>
          {value.shortcut ? (
            <Button
              type="button"
              variant="ghost"
              onClick={() => onChange({ ...value, shortcut: null })}
            >
              {t("privacy.screenLock.removeShortcut")}
            </Button>
          ) : null}
        </div>
        <FieldDescription id="screen-lock-shortcut-hint">
          {t("privacy.screenLock.shortcutHint")}
        </FieldDescription>
      </Field>
      {passkeysSupported() ? (
        <Field orientation="horizontal">
          <FieldContent>
            <FieldLabel htmlFor="screen-lock-passkey">
              {t("privacy.screenLock.allowPasskey")}
            </FieldLabel>
          </FieldContent>
          <Switch
            id="screen-lock-passkey"
            checked={value.allowPasskey}
            onCheckedChange={(allowPasskey) =>
              onChange({ ...value, allowPasskey })
            }
          />
        </Field>
      ) : null}
    </>
  )
}
