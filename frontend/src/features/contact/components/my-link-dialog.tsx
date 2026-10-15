import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { CopyIcon, Share2Icon } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { toast } from "@/components/ui/toast"
import { svgDataUrl } from "@/lib/image"
import { contactQueries } from "../api"

/** Link e QR Code para outras pessoas adicionarem o usuário (CT-01). */
export function MyLinkDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const link = useQuery({ ...contactQueries.myLink(), enabled: open })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("contacts.myLink")}</DialogTitle>
          <DialogDescription>
            {t("contacts.myLinkDescription")}
          </DialogDescription>
        </DialogHeader>
        {link.data ? (
          <div className="flex flex-col items-center gap-3">
            <img
              src={svgDataUrl(link.data.qrSvg)}
              alt={t("contacts.myLink")}
              className="size-48 rounded-lg bg-white p-2"
            />
            <code className="w-full rounded-md bg-muted p-2 text-center text-sm break-all">
              {link.data.url}
            </code>
          </div>
        ) : (
          <Skeleton className="mx-auto size-48" />
        )}
        <DialogFooter className="gap-2">
          {link.data && typeof navigator.share === "function" ? (
            <Button
              variant="outline"
              onClick={() =>
                void navigator
                  .share({ url: link.data.url })
                  .catch(() => undefined)
              }
            >
              <Share2Icon data-icon="inline-start" />
              {t("common.share")}
            </Button>
          ) : null}
          <Button
            disabled={!link.data}
            onClick={async () => {
              if (link.data) {
                await navigator.clipboard.writeText(link.data.url)
                toast.add({ title: t("common.copied") })
              }
            }}
          >
            <CopyIcon data-icon="inline-start" />
            {t("common.copy")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
