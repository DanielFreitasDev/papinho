import { useTranslation } from "react-i18next"
import { CopyIcon, DownloadIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { toast } from "@/components/ui/toast"

/** Mostra os códigos de recuperação uma única vez, com copiar e baixar (ID-05). */
export function RecoveryCodesDialog({
  codes,
  open,
  onAcknowledge,
}: {
  codes: string[]
  open: boolean
  onAcknowledge: () => void
}) {
  const { t } = useTranslation()
  const text = codes.join("\n")

  async function copy() {
    await navigator.clipboard.writeText(text)
    toast.add({ title: t("common.copied") })
  }

  function download() {
    const url = URL.createObjectURL(
      new Blob([text + "\n"], { type: "text/plain" })
    )
    const anchor = document.createElement("a")
    anchor.href = url
    anchor.download = "papinho-codigos-de-recuperacao.txt"
    anchor.click()
    URL.revokeObjectURL(url)
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => (!next ? onAcknowledge() : undefined)}
    >
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{t("auth.recoveryCodesTitle")}</DialogTitle>
          <DialogDescription>
            {t("auth.recoveryCodesDescription")}
          </DialogDescription>
        </DialogHeader>
        <ol
          className="grid grid-cols-2 gap-2 rounded-lg bg-muted p-3 font-mono text-sm"
          aria-label={t("auth.recoveryCodesTitle")}
        >
          {codes.map((code) => (
            <li key={code} className="select-all">
              {code}
            </li>
          ))}
        </ol>
        <DialogFooter className="flex-wrap gap-2">
          <Button type="button" variant="outline" onClick={copy}>
            <CopyIcon data-icon="inline-start" />
            {t("common.copy")}
          </Button>
          <Button type="button" variant="outline" onClick={download}>
            <DownloadIcon data-icon="inline-start" />
            {t("auth.download")}
          </Button>
          <Button type="button" onClick={onAcknowledge}>
            {t("auth.recoveryCodesAck")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
