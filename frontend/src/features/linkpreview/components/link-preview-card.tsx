import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { PlayIcon } from "lucide-react"
import { cn } from "cn"

import { linkPreviewQueries, type LinkPreview } from "../api"

/** Endereço final do iframe: autoplay só depois do clique (o carregamento já é a ação do usuário). */
function embedUrl(preview: LinkPreview): string {
  const src = new URL(preview.embedSrc!)
  if (preview.embedProvider === "YOUTUBE") {
    src.searchParams.set("autoplay", "1")
  }
  return src.toString()
}

/**
 * Prévia do primeiro link da mensagem (MD-04). Players do YouTube (sem cookies), Vimeo e Spotify só carregam
 * depois do clique (MD-05): até lá nada de terceiros é baixado.
 */
export function LinkPreviewCard({ url }: { url: string }) {
  const { t } = useTranslation()
  const preview = useQuery(linkPreviewQueries.preview(url))
  const [playing, setPlaying] = useState(false)
  const data = preview.data
  if (!data?.available) {
    return null
  }
  const provider = data.embedProvider
  const title = data.title ?? data.url
  if (provider && data.embedSrc) {
    return (
      <div
        className="flex flex-col gap-1 overflow-hidden rounded-md bg-black/5 dark:bg-white/10"
        onClick={(event) => event.stopPropagation()}
      >
        {playing ? (
          <iframe
            src={embedUrl(data)}
            title={t("linkPreview.player", { title })}
            className={cn(
              "w-full border-0",
              provider === "SPOTIFY" ? "h-[152px]" : "aspect-video"
            )}
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            allowFullScreen
            referrerPolicy="strict-origin-when-cross-origin"
            sandbox="allow-scripts allow-same-origin allow-presentation allow-popups"
          />
        ) : (
          <button
            type="button"
            onClick={() => setPlaying(true)}
            className="group/play relative flex aspect-video w-full items-center justify-center overflow-hidden bg-black outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            {data.imageUrl ? (
              <img
                src={data.imageUrl}
                alt=""
                className="absolute inset-0 size-full object-cover opacity-80"
              />
            ) : null}
            <span className="relative flex size-14 items-center justify-center rounded-full bg-black/70 text-white group-hover/play:bg-red-600">
              <PlayIcon aria-hidden="true" className="size-7" />
            </span>
            <span className="sr-only">
              {t("linkPreview.load", {
                provider: t(`linkPreview.provider.${provider}`),
                title,
              })}
            </span>
          </button>
        )}
        <p className="px-2 pb-2 text-xs">
          <span className="font-medium">{title}</span>
          <span className="block opacity-80">
            {t("linkPreview.privacyNote", {
              provider: t(`linkPreview.provider.${provider}`),
            })}
          </span>
        </p>
      </div>
    )
  }
  return (
    <a
      href={data.url}
      target="_blank"
      rel="noopener noreferrer nofollow"
      onClick={(event) => event.stopPropagation()}
      className="flex overflow-hidden rounded-md bg-black/5 outline-none hover:bg-black/10 focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-white/10"
    >
      {data.imageUrl ? (
        <img
          src={data.imageUrl}
          alt=""
          loading="lazy"
          className="size-20 shrink-0 object-cover"
        />
      ) : null}
      <span className="flex min-w-0 flex-col gap-0.5 p-2">
        {data.siteName ? (
          <span className="truncate text-xs opacity-80">{data.siteName}</span>
        ) : null}
        <span className="line-clamp-2 text-sm font-medium">{title}</span>
        {data.description ? (
          <span className="line-clamp-2 text-xs opacity-80">
            {data.description}
          </span>
        ) : null}
      </span>
    </a>
  )
}
