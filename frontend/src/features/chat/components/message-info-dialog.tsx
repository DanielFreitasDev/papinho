import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { formatDateTime } from "@/lib/format"
import { chatQueries } from "../api"
import { plainText } from "../format/parse"

/** "Dados da mensagem": quando cada destinatário recebeu e leu (MS-02). */
export function MessageInfoDialog({
  messageId,
  onClose,
}: {
  messageId: string | null
  onClose: () => void
}) {
  const { t } = useTranslation()
  const info = useQuery({
    ...chatQueries.info(messageId ?? ""),
    enabled: messageId !== null,
  })
  return (
    <Dialog
      open={messageId !== null}
      onOpenChange={(open) => !open && onClose()}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("chat.info")}</DialogTitle>
          <DialogDescription className="line-clamp-3">
            {info.data ? plainText(info.data.message.body) : ""}
          </DialogDescription>
        </DialogHeader>
        {info.isPending ? (
          <Skeleton className="h-16" />
        ) : (
          <dl className="flex flex-col gap-3 text-sm">
            {info.data?.recipients.map((recipient) => (
              <div key={recipient.userId} className="flex flex-col gap-1">
                <dt className="font-medium">{recipient.displayName}</dt>
                <dd className="grid grid-cols-[auto_1fr] gap-x-3 text-muted-foreground">
                  <span>{t("chat.infoRead")}</span>
                  <span>
                    {recipient.readAt
                      ? formatDateTime(recipient.readAt)
                      : t("chat.infoPending")}
                  </span>
                  <span>{t("chat.infoDelivered")}</span>
                  <span>
                    {recipient.deliveredAt
                      ? formatDateTime(recipient.deliveredAt)
                      : t("chat.infoPending")}
                  </span>
                </dd>
              </div>
            ))}
          </dl>
        )}
      </DialogContent>
    </Dialog>
  )
}
