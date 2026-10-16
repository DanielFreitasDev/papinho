import { useState, type FormEvent } from "react"
import { useNavigate } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"
import { LockIcon, LockOpenIcon } from "lucide-react"

import { PasswordInput } from "@/components/password-input"
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
  FieldError,
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
import { toast } from "@/components/ui/toast"
import { useLockConversation, useUnlockPermanently } from "@/features/privacy"
import { errorMessage } from "@/lib/api-client"
import { useReadReceipts } from "../api"
import type { Conversation } from "../types"

/** Confirmação de leitura nesta conversa (PV-06) e trancar ou destrancar (PV-03), nos painéis de detalhes. */
export function ConversationPrivacy({
  conversation,
}: {
  conversation: Conversation
}) {
  return (
    <div className="flex flex-col gap-3">
      <ReadReceiptsSetting conversation={conversation} />
      <LockSetting conversation={conversation} />
    </div>
  )
}

function ReadReceiptsSetting({ conversation }: { conversation: Conversation }) {
  const { t } = useTranslation()
  const change = useReadReceipts(conversation.id)
  const id = `read-receipts-${conversation.id}`
  const group = conversation.kind === "GROUP"
  const items = [
    {
      value: "default",
      label: group
        ? t("privacy.conversation.defaultGroup")
        : t("privacy.conversation.defaultDirect"),
    },
    { value: "on", label: t("privacy.conversation.on") },
    { value: "off", label: t("privacy.conversation.off") },
  ]
  const value =
    conversation.readReceipts === null
      ? "default"
      : conversation.readReceipts
        ? "on"
        : "off"
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-xs font-medium text-muted-foreground">
        {t("privacy.readReceipts")}
      </label>
      <Select
        items={items}
        value={value}
        disabled={change.isPending}
        onValueChange={(next) =>
          change.mutate(next === "on" ? true : next === "off" ? false : null, {
            onError: (error) =>
              toast.add({
                title: errorMessage(error, t("common.genericError")),
                type: "error",
              }),
          })
        }
      >
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {items.map((item) => (
              <SelectItem key={item.value} value={item.value}>
                {item.label}
              </SelectItem>
            ))}
          </SelectGroup>
        </SelectContent>
      </Select>
      <p className="text-xs text-muted-foreground">
        {t("privacy.conversation.reciprocity")}
      </p>
    </div>
  )
}

function LockSetting({ conversation }: { conversation: Conversation }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const unlock = useUnlockPermanently()
  if (conversation.locked) {
    return (
      <Button
        variant="outline"
        className="justify-start"
        disabled={unlock.isPending}
        onClick={() =>
          unlock.mutate(conversation.id, {
            onSuccess: () =>
              toast.add({ title: t("privacy.locked.unlockedForGood") }),
            onError: (error) =>
              toast.add({
                title: errorMessage(error, t("common.genericError")),
                type: "error",
              }),
          })
        }
      >
        <LockOpenIcon />
        {t("privacy.locked.unlockForGood")}
      </Button>
    )
  }
  return (
    <>
      <Button
        variant="outline"
        className="justify-start"
        onClick={() => setOpen(true)}
      >
        <LockIcon />
        {t("privacy.locked.lock")}
      </Button>
      {open ? (
        <LockDialog
          conversationId={conversation.id}
          onClose={() => setOpen(false)}
          onLocked={() => {
            setOpen(false)
            void navigate({ to: "/locked" })
          }}
        />
      ) : null}
    </>
  )
}

function LockDialog({
  conversationId,
  onClose,
  onLocked,
}: {
  conversationId: string
  onClose: () => void
  onLocked: () => void
}) {
  const { t } = useTranslation()
  const lock = useLockConversation()
  const [code, setCode] = useState("")
  const [confirm, setConfirm] = useState("")
  const [error, setError] = useState<string | null>(null)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (code !== confirm) {
      setError(t("privacy.locked.mismatch"))
      return
    }
    setError(null)
    lock.mutate(
      { conversationId, code },
      {
        onSuccess: () => {
          toast.add({ title: t("privacy.locked.lockedToast") })
          onLocked()
        },
        onError: (failure) =>
          setError(errorMessage(failure, t("common.genericError"))),
      }
    )
  }

  return (
    <Dialog open onOpenChange={(next) => (next ? undefined : onClose())}>
      <DialogContent>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{t("privacy.locked.lockTitle")}</DialogTitle>
            <DialogDescription>
              {t("privacy.locked.lockDescription")}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="lock-code">
                {t("privacy.locked.code")}
              </FieldLabel>
              <PasswordInput
                id="lock-code"
                autoComplete="new-password"
                minLength={4}
                maxLength={64}
                value={code}
                onChange={(event) => setCode(event.target.value)}
              />
              <FieldDescription>
                {t("privacy.locked.codeHint")}
              </FieldDescription>
            </Field>
            <Field data-invalid={error ? true : undefined}>
              <FieldLabel htmlFor="lock-code-confirm">
                {t("privacy.locked.confirm")}
              </FieldLabel>
              <PasswordInput
                id="lock-code-confirm"
                autoComplete="new-password"
                value={confirm}
                aria-invalid={error ? true : undefined}
                onChange={(event) => setConfirm(event.target.value)}
              />
              {error ? <FieldError>{error}</FieldError> : null}
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={lock.isPending || code.length < 4}>
              {t("privacy.locked.lock")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
