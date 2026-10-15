import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { SettingsIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { mediaUrl } from "@/features/media"
import { stickerQueries, type Sticker } from "./api"

/** Figurinhas favoritas e os pacotes da instância; tocar envia (MS-10). */
export function StickerPanel({
  onSelect,
  onManage,
}: {
  onSelect: (sticker: Sticker) => void
  /** Abre "Gerenciar figurinhas" (fora do popover, que fecha). */
  onManage: () => void
}) {
  const { t } = useTranslation()
  const packs = useQuery(stickerQueries.packs())
  const favorites = useQuery(stickerQueries.favorites())

  const grid = (stickers: Sticker[]) => (
    <div className="grid grid-cols-4 gap-1">
      {stickers.map((sticker) => (
        <button
          key={sticker.id}
          type="button"
          aria-label={t("sticker.send", { emoji: sticker.emoji ?? "" })}
          onClick={() => onSelect(sticker)}
          className="flex aspect-square items-center justify-center rounded-md outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <img
            src={mediaUrl(sticker.mediaId)}
            alt=""
            loading="lazy"
            className="size-full object-contain"
          />
        </button>
      ))}
    </div>
  )

  return (
    <div className="flex h-72 w-[min(20rem,calc(100vw-2rem))] flex-col gap-2 overflow-y-auto">
      {packs.isPending ? (
        <Skeleton className="h-full w-full" />
      ) : (
        <>
          {favorites.data && favorites.data.length > 0 ? (
            <section className="flex flex-col gap-1">
              <h3 className="text-xs font-medium text-muted-foreground">
                {t("sticker.favorites")}
              </h3>
              {grid(favorites.data)}
            </section>
          ) : null}
          {packs.data?.map((pack) => (
            <section key={pack.id} className="flex flex-col gap-1">
              <h3 className="text-xs font-medium text-muted-foreground">
                {pack.name}
              </h3>
              {pack.stickers.length > 0 ? (
                grid(pack.stickers)
              ) : (
                <p className="text-xs text-muted-foreground">
                  {t("sticker.emptyPack")}
                </p>
              )}
            </section>
          ))}
          {packs.data?.length === 0 ? (
            <p className="py-4 text-center text-sm text-muted-foreground">
              {t("sticker.empty")}
            </p>
          ) : null}
        </>
      )}
      <Button
        variant="outline"
        size="sm"
        className="mt-auto"
        onClick={onManage}
      >
        <SettingsIcon data-icon="inline-start" />
        {t("sticker.manage")}
      </Button>
    </div>
  )
}
