import { useTranslation } from "react-i18next"

import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { useDeleteForEveryone, useHideMessages } from "../api"

/**
 * Apagar mensagens (MS-06): "para todos" só aparece quando todas são minhas, dentro da janela e ainda não
 * apagadas; "para mim" sempre.
 */
export function DeleteMessagesDialog({
  conversationId,
  messageIds,
  canDeleteForEveryone,
  onClose,
}: {
  conversationId: string
  messageIds: string[]
  canDeleteForEveryone: boolean
  onClose: (deleted: boolean) => void
}) {
  const { t } = useTranslation()
  const forEveryone = useDeleteForEveryone()
  const forMe = useHideMessages(conversationId)
  const busy = forEveryone.isPending || forMe.isPending

  const fail = (error: unknown) =>
    toast.add({
      title: errorMessage(error, t("common.genericError")),
      type: "error",
    })

  return (
    <AlertDialog
      open={messageIds.length > 0}
      onOpenChange={(open) => {
        if (!open) {
          onClose(false)
        }
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            {t("chat.deleteTitle", { count: messageIds.length })}
          </AlertDialogTitle>
          <AlertDialogDescription>
            {canDeleteForEveryone
              ? t("chat.deleteDescription")
              : t("chat.deleteForMeDescription")}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>{t("common.cancel")}</AlertDialogCancel>
          <Button
            variant="outline"
            disabled={busy}
            onClick={() =>
              forMe.mutate(messageIds, {
                onSuccess: () => onClose(true),
                onError: fail,
              })
            }
          >
            {t("chat.deleteForMe")}
          </Button>
          {canDeleteForEveryone ? (
            <Button
              variant="destructive"
              disabled={busy}
              onClick={async () => {
                try {
                  for (const id of messageIds) {
                    await forEveryone.mutateAsync(id)
                  }
                  onClose(true)
                } catch (error) {
                  fail(error)
                }
              }}
            >
              {t("chat.deleteForEveryone")}
            </Button>
          ) : null}
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
