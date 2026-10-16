import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { LockKeyholeIcon } from "lucide-react"

import { PasswordInput } from "@/components/password-input"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { errorMessage } from "@/lib/api-client"
import { useUnlockConversations } from "../api"

/** Pede o código das conversas trancadas (PV-03); o token resultante abre a pasta e as conversas do código. */
export function UnlockForm({
  title,
  onUnlocked,
}: {
  title: string
  onUnlocked?: () => void
}) {
  const { t } = useTranslation()
  const unlock = useUnlockConversations()
  const [code, setCode] = useState("")
  const [error, setError] = useState<string | null>(null)

  function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    unlock.mutate(code, {
      onSuccess: () => {
        setCode("")
        onUnlocked?.()
      },
      onError: (failure) => {
        setCode("")
        setError(errorMessage(failure, t("privacy.locked.wrongCode")))
      },
    })
  }

  return (
    <form
      noValidate
      onSubmit={submit}
      className="mx-auto flex w-full max-w-xs flex-col items-center gap-4 p-6"
    >
      <LockKeyholeIcon aria-hidden="true" className="size-8 text-primary" />
      <h1 className="text-center text-lg font-semibold">{title}</h1>
      <Field className="w-full" data-invalid={error ? true : undefined}>
        <FieldLabel htmlFor="unlock-code">
          {t("privacy.locked.code")}
        </FieldLabel>
        <PasswordInput
          id="unlock-code"
          autoComplete="off"
          autoFocus
          value={code}
          aria-invalid={error ? true : undefined}
          onChange={(event) => setCode(event.target.value)}
        />
        {error ? <FieldError>{error}</FieldError> : null}
      </Field>
      <Button
        type="submit"
        className="w-full"
        disabled={unlock.isPending || code.length === 0}
      >
        {t("privacy.locked.open")}
      </Button>
    </form>
  )
}
