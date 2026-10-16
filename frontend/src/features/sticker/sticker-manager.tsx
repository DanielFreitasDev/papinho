import { useRef, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  PlusIcon,
  StarIcon,
  StarOffIcon,
  Trash2Icon,
  UploadIcon,
} from "lucide-react"

import { QueryState } from "@/components/query-state"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { mediaUrl, uploadFile } from "@/features/media"
import { errorMessage } from "@/lib/api-client"
import { stickerQueries, useStickerAction } from "./api"

/**
 * Gerenciar figurinhas (MS-10): criar pacote, enviar WebP (estáticos ou animados) para os meus pacotes, tirar
 * figurinhas e favoritar as de qualquer pacote.
 */
export function StickerManager({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const packs = useQuery({ ...stickerQueries.packs(), enabled: open })
  const action = useStickerAction()
  const [name, setName] = useState("")
  const [uploading, setUploading] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const target = useRef<string | null>(null)

  const fail = (error: unknown) =>
    toast.add({
      title: errorMessage(error, t("common.genericError")),
      type: "error",
    })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("sticker.manage")}</DialogTitle>
          <DialogDescription>
            {t("sticker.manageDescription")}
          </DialogDescription>
        </DialogHeader>
        <form
          className="flex gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (name.trim()) {
              action.mutate(
                { action: "createPack", name: name.trim() },
                { onSuccess: () => setName(""), onError: fail }
              )
            }
          }}
        >
          <Input
            aria-label={t("sticker.packName")}
            placeholder={t("sticker.packName")}
            maxLength={64}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <Button type="submit" disabled={name.trim() === ""}>
            <PlusIcon data-icon="inline-start" />
            {t("sticker.createPack")}
          </Button>
        </form>
        <input
          ref={input}
          type="file"
          accept="image/webp"
          multiple
          hidden
          tabIndex={-1}
          aria-hidden="true"
          onChange={async (event) => {
            const files = [...(event.target.files ?? [])]
            event.target.value = ""
            const packId = target.current
            if (!packId) {
              return
            }
            setUploading(packId)
            try {
              for (const file of files) {
                const mediaId = await uploadFile(file, () => {}).done
                await action.mutateAsync({ action: "add", packId, mediaId })
              }
            } catch (error) {
              fail(error)
            } finally {
              setUploading(null)
            }
          }}
        />
        <div className="flex max-h-[50dvh] flex-col gap-4 overflow-y-auto">
          {packs.data ? null : (
            <QueryState query={packs}>{() => null}</QueryState>
          )}
          {packs.data?.map((pack) => (
            <section key={pack.id} className="flex flex-col gap-2">
              <div className="flex items-center gap-2">
                <h3 className="flex-1 truncate text-sm font-medium">
                  {pack.name}
                </h3>
                {pack.mine ? (
                  <>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={uploading !== null}
                      onClick={() => {
                        target.current = pack.id
                        input.current?.click()
                      }}
                    >
                      {uploading === pack.id ? (
                        <Spinner data-icon="inline-start" />
                      ) : (
                        <UploadIcon data-icon="inline-start" />
                      )}
                      {t("sticker.add")}
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={t("sticker.removePack", { name: pack.name })}
                      onClick={() =>
                        action.mutate(
                          { action: "removePack", packId: pack.id },
                          { onError: fail }
                        )
                      }
                    >
                      <Trash2Icon />
                    </Button>
                  </>
                ) : null}
              </div>
              <ul className="grid grid-cols-4 gap-2">
                {pack.stickers.map((sticker) => (
                  <li
                    key={sticker.id}
                    className="flex flex-col items-center gap-1"
                  >
                    <img
                      src={mediaUrl(sticker.mediaId)}
                      alt={sticker.emoji ?? t("sticker.label")}
                      className="size-14 object-contain"
                    />
                    <span className="flex">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label={
                          sticker.favorite
                            ? t("sticker.unfavorite")
                            : t("sticker.favorite")
                        }
                        onClick={() =>
                          action.mutate({
                            action: "favorite",
                            stickerId: sticker.id,
                            favorite: !sticker.favorite,
                          })
                        }
                      >
                        {sticker.favorite ? <StarOffIcon /> : <StarIcon />}
                      </Button>
                      {pack.mine ? (
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label={t("sticker.remove")}
                          onClick={() =>
                            action.mutate(
                              { action: "remove", stickerId: sticker.id },
                              { onError: fail }
                            )
                          }
                        >
                          <Trash2Icon />
                        </Button>
                      ) : null}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}
