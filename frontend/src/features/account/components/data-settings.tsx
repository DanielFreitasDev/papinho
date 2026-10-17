import { useState } from "react"
import { useNavigate } from "@tanstack/react-router"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { DownloadIcon, Trash2Icon } from "lucide-react"

import { PasswordInput } from "@/components/password-input"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button, buttonVariants } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { Label } from "@/components/ui/label"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { authKeys } from "@/features/auth"
import { apiFetch, errorMessage } from "@/lib/api-client"
import { runLogoutCleanups } from "@/lib/logout-cleanup"
import { SettingsSection } from "./settings-section"

/**
 * Seus dados (BK-02, LGPD): baixar tudo o que é seu num ZIP e excluir a conta com a senha. As mensagens que você
 * mandou continuam com quem recebeu, como "Conta apagada".
 */
export function DataSettings() {
  const { t } = useTranslation()
  const [withFiles, setWithFiles] = useState(false)
  const [deleting, setDeleting] = useState(false)
  return (
    <>
      <SettingsSection
        title={t("account.data.title")}
        description={t("account.data.description")}
      >
        <div className="flex flex-col gap-3">
          <Label className="flex items-center gap-2 text-sm font-normal">
            <Checkbox
              checked={withFiles}
              onCheckedChange={(checked) => setWithFiles(checked)}
            />
            {t("account.data.withFiles")}
          </Label>
          <a
            href={`/api/v1/account/export?files=${withFiles}`}
            download
            className={buttonVariants({
              variant: "outline",
              className: "self-start",
            })}
          >
            <DownloadIcon data-icon="inline-start" />
            {t("account.data.export")}
          </a>
        </div>
      </SettingsSection>
      <SettingsSection
        title={t("account.delete.title")}
        description={t("account.delete.description")}
      >
        <Button
          variant="destructive"
          className="self-start"
          onClick={() => setDeleting(true)}
        >
          <Trash2Icon data-icon="inline-start" />
          {t("account.delete.action")}
        </Button>
      </SettingsSection>
      {deleting ? (
        <DeleteAccountDialog onClose={() => setDeleting(false)} />
      ) : null}
    </>
  )
}

function DeleteAccountDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [password, setPassword] = useState("")
  const remove = useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/account", {
        method: "DELETE",
        json: { password },
      }),
    onSuccess: async () => {
      // A assinatura de push já saiu com a conta no servidor; aqui some a deste navegador e os dados offline.
      await runLogoutCleanups()
      queryClient.clear()
      queryClient.setQueryData(authKeys.me, null)
      toast.add({ title: t("account.delete.done") })
      await navigate({ to: "/login" })
    },
  })
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent>
        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            // eslint-disable-next-line no-restricted-syntax -- o erro aparece no Alert do diálogo (remove.isError)
            remove.mutate()
          }}
        >
          <DialogHeader>
            <DialogTitle>{t("account.delete.confirmTitle")}</DialogTitle>
            <DialogDescription>
              {t("account.delete.confirmDescription")}
            </DialogDescription>
          </DialogHeader>
          {remove.isError ? (
            <Alert variant="destructive" role="alert">
              <AlertDescription>
                {errorMessage(remove.error, t("common.genericError"))}
              </AlertDescription>
            </Alert>
          ) : null}
          <Field>
            <FieldLabel htmlFor="delete-password">
              {t("account.delete.password")}
            </FieldLabel>
            <PasswordInput
              id="delete-password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t("common.cancel")}
            </Button>
            <Button
              type="submit"
              variant="destructive"
              disabled={!password || remove.isPending}
            >
              {remove.isPending ? <Spinner data-icon="inline-start" /> : null}
              {t("account.delete.confirm")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
