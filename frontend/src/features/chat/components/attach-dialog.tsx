import { useEffect, useId, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { formatBytes, guessKind } from "@/features/media"
import { KindIcon } from "./attachment-view"

/**
 * Confirma os arquivos escolhidos (botão, colar ou arrastar) antes de enviar, com legenda opcional na
 * primeira mensagem (MD-01, MD-02, MS-13).
 */
export function AttachDialog({
  allowViewOnce = true,
  files,
  onClose,
  onSend,
}: {
  /** Visualização única não vale para mensagens salvas nem listas de transmissão. */
  allowViewOnce?: boolean
  files: File[]
  onClose: () => void
  onSend: (files: File[], caption: string, viewOnce: boolean) => void
}) {
  const { t, i18n } = useTranslation()
  const [caption, setCaption] = useState("")
  const [viewOnce, setViewOnce] = useState(false)
  const captionId = useId()
  // Visualização única (MS-08): um arquivo só, de foto, vídeo ou áudio.
  const canViewOnce =
    allowViewOnce && files.length === 1 && guessKind(files[0]) !== "DOCUMENT"
  const previews = useMemo(
    () =>
      files.map((file) =>
        guessKind(file) === "IMAGE" ? URL.createObjectURL(file) : null
      ),
    [files]
  )
  useEffect(
    () => () => previews.forEach((url) => url && URL.revokeObjectURL(url)),
    [previews]
  )

  return (
    <Dialog
      open={files.length > 0}
      onOpenChange={(open) => {
        if (!open) {
          setCaption("")
          setViewOnce(false)
          onClose()
        }
      }}
    >
      <DialogContent>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            onSend(files, caption.trim(), canViewOnce && viewOnce)
            setCaption("")
            setViewOnce(false)
          }}
        >
          <DialogHeader>
            <DialogTitle>
              {t("media.sendFiles", { count: files.length })}
            </DialogTitle>
            <DialogDescription>{t("media.sendDescription")}</DialogDescription>
          </DialogHeader>
          <ul className="flex max-h-72 flex-col gap-2 overflow-y-auto">
            {files.map((file, index) => (
              <li
                key={`${file.name}-${index}`}
                className="flex items-center gap-3 rounded-md border p-2"
              >
                {previews[index] ? (
                  <img
                    src={previews[index]}
                    alt=""
                    className="size-12 shrink-0 rounded object-cover"
                  />
                ) : (
                  <KindIcon
                    kind={guessKind(file)}
                    className="size-8 shrink-0"
                  />
                )}
                <span className="flex min-w-0 flex-col">
                  <span className="truncate text-sm font-medium">
                    {file.name || t("media.pastedImage")}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {formatBytes(file.size, i18n.language)}
                  </span>
                </span>
              </li>
            ))}
          </ul>
          <Field>
            <FieldLabel htmlFor={captionId}>{t("media.caption")}</FieldLabel>
            <Textarea
              id={captionId}
              value={caption}
              maxLength={4096}
              onChange={(event) => setCaption(event.target.value)}
            />
          </Field>
          {canViewOnce ? (
            <Field orientation="horizontal">
              <Switch
                id={`${captionId}-once`}
                checked={viewOnce}
                onCheckedChange={setViewOnce}
              />
              <FieldLabel htmlFor={`${captionId}-once`}>
                {t("viewOnce.toggle")}
              </FieldLabel>
            </Field>
          ) : null}
          {viewOnce ? (
            <p className="text-xs text-muted-foreground">
              {t("viewOnce.screenshotWarning")}
            </p>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t("common.cancel")}
            </Button>
            <Button type="submit">{t("chat.send")}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
