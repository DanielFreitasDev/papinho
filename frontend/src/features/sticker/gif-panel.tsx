import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { stickerQueries, type GifResult } from "./api"

/** Busca de GIFs pelo servidor (MS-10); prévias e envio passam pelo Papinho, nunca direto pelo provedor. */
export function GifPanel({ onSelect }: { onSelect: (gif: GifResult) => void }) {
  const { t } = useTranslation()
  const [query, setQuery] = useState("")
  const term = useDebouncedValue(query.trim(), 400)
  const gifs = useQuery(stickerQueries.gifs(term))
  return (
    <div className="flex h-72 w-[min(20rem,calc(100vw-2rem))] flex-col gap-2">
      <Input
        type="search"
        aria-label={t("gif.search")}
        placeholder={t("gif.search")}
        value={query}
        onChange={(event) => setQuery(event.target.value)}
      />
      {gifs.isPending ? (
        <Skeleton className="flex-1" />
      ) : gifs.isError ? (
        <p className="py-4 text-center text-sm text-muted-foreground">
          {t("gif.unavailable")}
        </p>
      ) : (
        <div className="grid flex-1 grid-cols-3 content-start gap-1 overflow-y-auto">
          {gifs.data.map((gif) => (
            <button
              key={gif.id}
              type="button"
              aria-label={gif.title || t("gif.label")}
              onClick={() => onSelect(gif)}
              className="aspect-square overflow-hidden rounded-md outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
            >
              <img
                src={`/api/v1/gifs/${gif.id}/preview`}
                alt=""
                loading="lazy"
                className="size-full object-cover"
              />
            </button>
          ))}
        </div>
      )}
      <p className="text-[0.7rem] text-muted-foreground">
        {t("gif.poweredBy")}
      </p>
    </div>
  )
}
