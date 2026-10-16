import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { KeyRoundIcon, TrashIcon } from "lucide-react"

import { ConfirmDialog } from "@/components/confirm-dialog"
import { PasswordInput } from "@/components/password-input"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import { OtpInput } from "@/components/otp-input"
import { Input } from "@/components/ui/input"
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemTitle,
} from "@/components/ui/item"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import {
  authQueries,
  passwordSchema,
  RecoveryCodesDialog,
} from "@/features/auth"
import { errorMessage } from "@/lib/api-client"
import { formatDateTime } from "@/lib/format"
import { svgDataUrl } from "@/lib/image"
import {
  deletePasskey,
  passkeysSupported,
  registerPasskey,
} from "@/lib/webauthn"
import {
  accountKeys,
  accountQueries,
  useChangeEmail,
  useChangePassword,
  useConfirmTotp,
  useDisableTotp,
  useRegenerateRecoveryCodes,
  useRemoveEmail,
  useStartTotp,
  type TotpEnrollment,
} from "../api"
import { SettingsSection } from "./settings-section"

/** Senha, TOTP, passkeys, códigos e e-mail de recuperação (ID-04, ID-05). */
export function SecuritySettings() {
  return (
    <div className="flex flex-col gap-4">
      <PasswordSection />
      <PasskeysSection />
      <TotpSection />
      <RecoveryCodesSection />
      <EmailSection />
    </div>
  )
}

function PasswordSection() {
  const { t } = useTranslation()
  const change = useChangePassword()
  const [current, setCurrent] = useState("")
  const [next, setNext] = useState("")
  const [error, setError] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    const parsed = passwordSchema.safeParse(next)
    if (!parsed.success) {
      setError(t(parsed.error.issues[0]?.message ?? "validation.passwordMin"))
      return
    }
    try {
      await change.mutateAsync({ currentPassword: current, newPassword: next })
      setCurrent("")
      setNext("")
      toast.add({ title: t("security.passwordChanged") })
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
    }
  }

  return (
    <SettingsSection title={t("security.password")}>
      <form onSubmit={submit} noValidate>
        <FieldGroup>
          {error ? (
            <Alert variant="destructive" role="alert">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
          <Field>
            <FieldLabel htmlFor="security-current">
              {t("security.currentPassword")}
            </FieldLabel>
            <PasswordInput
              id="security-current"
              autoComplete="current-password"
              value={current}
              onChange={(event) => setCurrent(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="security-new">
              {t("auth.newPassword")}
            </FieldLabel>
            <PasswordInput
              id="security-new"
              autoComplete="new-password"
              value={next}
              onChange={(event) => setNext(event.target.value)}
            />
            <FieldDescription>{t("auth.passwordHint")}</FieldDescription>
          </Field>
          <Field orientation="horizontal">
            <Button
              type="submit"
              disabled={change.isPending || !current || !next}
            >
              {change.isPending ? <Spinner data-icon="inline-start" /> : null}
              {t("security.changePassword")}
            </Button>
          </Field>
        </FieldGroup>
      </form>
    </SettingsSection>
  )
}

function PasskeysSection() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const passkeys = useQuery(accountQueries.passkeys())
  const [label, setLabel] = useState("")
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const supported = passkeysSupported()

  async function add() {
    setError(null)
    setPending(true)
    try {
      await registerPasskey(label.trim() || navigator.platform || "Passkey")
      setLabel("")
      toast.add({ title: t("security.passkeyAdded") })
      await queryClient.invalidateQueries({ queryKey: accountKeys.passkeys })
    } catch (caught) {
      setError(errorMessage(caught, t("auth.passkeyFailed")))
    } finally {
      setPending(false)
    }
  }

  async function remove(id: string) {
    await deletePasskey(id)
    await queryClient.invalidateQueries({ queryKey: accountKeys.passkeys })
  }

  return (
    <SettingsSection
      title={t("security.passkeys")}
      description={t("security.passkeysDescription")}
    >
      <FieldGroup>
        {!supported ? (
          <Alert>
            <AlertDescription>
              {t("security.passkeysUnsupported")}
            </AlertDescription>
          </Alert>
        ) : null}
        {error ? (
          <Alert variant="destructive" role="alert">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}
        {passkeys.data && passkeys.data.length > 0 ? (
          <ItemGroup>
            {passkeys.data.map((passkey) => (
              <Item key={passkey.id} variant="outline" size="sm">
                <ItemContent>
                  <ItemTitle>{passkey.label}</ItemTitle>
                  {passkey.lastUsedAt ? (
                    <ItemDescription>
                      {t("security.lastUsed", {
                        date: formatDateTime(passkey.lastUsedAt),
                      })}
                    </ItemDescription>
                  ) : null}
                </ItemContent>
                <ItemActions>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`${t("common.delete")}: ${passkey.label}`}
                    onClick={() => void remove(passkey.id)}
                  >
                    <TrashIcon />
                  </Button>
                </ItemActions>
              </Item>
            ))}
          </ItemGroup>
        ) : (
          <FieldDescription>{t("security.passkeysNone")}</FieldDescription>
        )}
        {supported ? (
          <Field orientation="responsive">
            <FieldLabel htmlFor="passkey-label" className="sr-only">
              {t("security.passkeyLabel")}
            </FieldLabel>
            <Input
              id="passkey-label"
              placeholder={t("security.passkeyLabel")}
              value={label}
              maxLength={60}
              onChange={(event) => setLabel(event.target.value)}
            />
            <Button onClick={add} disabled={pending}>
              {pending ? (
                <Spinner data-icon="inline-start" />
              ) : (
                <KeyRoundIcon data-icon="inline-start" />
              )}
              {t("security.addPasskey")}
            </Button>
          </Field>
        ) : null}
      </FieldGroup>
    </SettingsSection>
  )
}

function TotpSection() {
  const { t } = useTranslation()
  const me = useQuery(authQueries.me())
  const start = useStartTotp()
  const confirm = useConfirmTotp()
  const disable = useDisableTotp()
  const [enrollment, setEnrollment] = useState<TotpEnrollment | null>(null)
  const [code, setCode] = useState("")
  const [password, setPassword] = useState("")
  const [disabling, setDisabling] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const enabled = me.data?.account.totpEnabled ?? false

  async function begin() {
    setError(null)
    setCode("")
    setEnrollment(await start.mutateAsync())
  }

  async function finish() {
    setError(null)
    try {
      await confirm.mutateAsync(code)
      setEnrollment(null)
      toast.add({ title: t("security.totpEnabled") })
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
    }
  }

  return (
    <SettingsSection
      title={t("security.totp")}
      action={
        <Badge variant={enabled ? "default" : "secondary"} className="w-fit">
          {enabled ? t("security.totpOn") : t("security.totpOff")}
        </Badge>
      }
    >
      {enabled ? (
        <Button variant="outline" onClick={() => setDisabling(true)}>
          {t("security.totpDisable")}
        </Button>
      ) : (
        <Button variant="outline" onClick={begin} disabled={start.isPending}>
          {start.isPending ? <Spinner data-icon="inline-start" /> : null}
          {t("security.totpEnable")}
        </Button>
      )}
      <Dialog
        open={enrollment !== null}
        onOpenChange={(open) => !open && setEnrollment(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("security.totp")}</DialogTitle>
            <DialogDescription>{t("security.totpScan")}</DialogDescription>
          </DialogHeader>
          {enrollment ? (
            <div className="flex flex-col items-center gap-3">
              <img
                src={svgDataUrl(enrollment.qrSvg)}
                alt={t("security.totp")}
                className="size-44 rounded-lg bg-white p-2"
              />
              <p className="text-sm">
                {t("security.totpKey")}:{" "}
                <code className="font-mono break-all select-all">
                  {enrollment.secret}
                </code>
              </p>
            </div>
          ) : null}
          {error ? (
            <Alert variant="destructive" role="alert">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
          <Field className="items-center">
            <FieldLabel htmlFor="totp-confirm">
              {t("auth.totpTitle")}
            </FieldLabel>
            <OtpInput id="totp-confirm" value={code} onChange={setCode} />
          </Field>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEnrollment(null)}>
              {t("common.cancel")}
            </Button>
            <Button
              onClick={finish}
              disabled={code.length !== 6 || confirm.isPending}
            >
              {confirm.isPending ? <Spinner data-icon="inline-start" /> : null}
              {t("common.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={disabling}
        onOpenChange={setDisabling}
        title={t("security.totpDisable")}
        confirmLabel={t("security.totpDisable")}
        onConfirm={async () => {
          try {
            await disable.mutateAsync(password)
            toast.add({ title: t("security.totpDisabled") })
          } catch (caught) {
            toast.add({
              title: errorMessage(caught, t("common.genericError")),
              type: "error",
            })
          }
          setPassword("")
        }}
      >
        <Field>
          <FieldLabel htmlFor="totp-disable-password">
            {t("security.currentPassword")}
          </FieldLabel>
          <PasswordInput
            id="totp-disable-password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </Field>
      </ConfirmDialog>
    </SettingsSection>
  )
}

function RecoveryCodesSection() {
  const { t } = useTranslation()
  const me = useQuery(authQueries.me())
  const regenerate = useRegenerateRecoveryCodes()
  const [asking, setAsking] = useState(false)
  const [password, setPassword] = useState("")
  const [codes, setCodes] = useState<string[] | null>(null)

  return (
    <SettingsSection
      title={t("security.recoveryCodes")}
      description={t("security.recoveryRemaining", {
        count: me.data?.account.remainingRecoveryCodes ?? 0,
      })}
    >
      <Button variant="outline" onClick={() => setAsking(true)}>
        {t("security.regenerate")}
      </Button>
      <ConfirmDialog
        open={asking}
        onOpenChange={setAsking}
        title={t("security.regenerate")}
        description={t("security.regenerateDescription")}
        confirmLabel={t("security.regenerate")}
        onConfirm={async () => {
          try {
            setCodes((await regenerate.mutateAsync(password)).recoveryCodes)
          } catch (caught) {
            toast.add({
              title: errorMessage(caught, t("common.genericError")),
              type: "error",
            })
          }
          setPassword("")
        }}
      >
        <Field>
          <FieldLabel htmlFor="recovery-password">
            {t("security.currentPassword")}
          </FieldLabel>
          <PasswordInput
            id="recovery-password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </Field>
      </ConfirmDialog>
      <RecoveryCodesDialog
        codes={codes ?? []}
        open={codes !== null}
        onAcknowledge={() => setCodes(null)}
      />
    </SettingsSection>
  )
}

/** E-mail opcional de recuperação (ID-05): só aparece quando a instância envia e-mails. */
function EmailSection() {
  const { t } = useTranslation()
  const me = useQuery(authQueries.me())
  const instance = useQuery(authQueries.instance())
  const change = useChangeEmail()
  const remove = useRemoveEmail()
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [removing, setRemoving] = useState(false)
  const [removePassword, setRemovePassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const account = me.data?.account
  if (!instance.data?.emailRecovery || !account) {
    return null
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await change.mutateAsync({ email: email.trim(), password })
      setEmail("")
      setPassword("")
      toast.add({ title: t("security.emailSent") })
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
    }
  }

  return (
    <SettingsSection
      title={t("security.email")}
      description={t("security.emailDescription")}
    >
      <FieldGroup>
        {account.email ? (
          <ItemGroup>
            <Item variant="outline" size="sm">
              <ItemContent>
                <ItemTitle className="break-all">{account.email}</ItemTitle>
                <ItemDescription>
                  {account.emailVerified
                    ? t("security.emailVerified")
                    : t("security.emailPending")}
                </ItemDescription>
              </ItemContent>
              <ItemActions>
                <Badge
                  variant={account.emailVerified ? "secondary" : "outline"}
                >
                  {account.emailVerified
                    ? t("security.emailVerifiedBadge")
                    : t("security.emailPendingBadge")}
                </Badge>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={t("security.emailRemove")}
                  onClick={() => setRemoving(true)}
                >
                  <TrashIcon />
                </Button>
              </ItemActions>
            </Item>
          </ItemGroup>
        ) : null}
        <form onSubmit={submit} noValidate>
          <FieldGroup>
            {error ? (
              <Alert variant="destructive" role="alert">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            <Field>
              <FieldLabel htmlFor="security-email">
                {account.email
                  ? t("security.emailNew")
                  : t("security.emailAddress")}
              </FieldLabel>
              <Input
                id="security-email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
              <FieldDescription>{t("security.emailHint")}</FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="security-email-password">
                {t("security.currentPassword")}
              </FieldLabel>
              <PasswordInput
                id="security-email-password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </Field>
            <Field orientation="horizontal">
              <Button
                type="submit"
                disabled={change.isPending || !email.trim() || !password}
              >
                {change.isPending ? <Spinner data-icon="inline-start" /> : null}
                {t("security.emailSave")}
              </Button>
            </Field>
          </FieldGroup>
        </form>
      </FieldGroup>
      <ConfirmDialog
        open={removing}
        onOpenChange={setRemoving}
        title={t("security.emailRemove")}
        description={t("security.emailRemoveDescription")}
        confirmLabel={t("security.emailRemove")}
        onConfirm={async () => {
          try {
            await remove.mutateAsync(removePassword)
            toast.add({ title: t("security.emailRemoved") })
          } catch (caught) {
            toast.add({
              title: errorMessage(caught, t("common.genericError")),
              type: "error",
            })
          }
          setRemovePassword("")
        }}
      >
        <Field>
          <FieldLabel htmlFor="email-remove-password">
            {t("security.currentPassword")}
          </FieldLabel>
          <PasswordInput
            id="email-remove-password"
            autoComplete="current-password"
            value={removePassword}
            onChange={(event) => setRemovePassword(event.target.value)}
          />
        </Field>
      </ConfirmDialog>
    </SettingsSection>
  )
}
