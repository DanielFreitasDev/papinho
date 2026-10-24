import { useEffect, useMemo, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { cn } from "cn"

import { SearchInput } from "@/components/search-input"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { authQueries } from "@/features/auth"
import {
  GROUPS,
  loadEmojis,
  pushRecent,
  readRecent,
  searchEmojis,
  type EmojiItem,
} from "./data"

/** Primeiro emoji de cada grupo, para o ícone da aba. */
const GROUP_ICONS: Record<number, string> = {
  0: "😀",
  1: "👋",
  3: "🐶",
  4: "🍕",
  5: "🚗",
  6: "⚽",
  7: "💡",
  8: "❤️",
  9: "🏳️",
}

/**
 * Seletor de emojis (MS-10): busca por nome e etiquetas no idioma da interface, recentes e abas por categoria.
 * Os dados vêm do emojibase empacotado no próprio app (sem CDN, dentro da CSP).
 */
export function EmojiPicker({
  onSelect,
}: {
  onSelect: (emoji: string) => void
}) {
  const { t, i18n } = useTranslation()
  const me = useQuery(authQueries.me())
  const userId = me.data?.account.id ?? "anon"
  const [emojis, setEmojis] = useState<EmojiItem[] | null>(null)
  const [query, setQuery] = useState("")
  const [group, setGroup] = useState<number | "recent">("recent")
  const [recent, setRecent] = useState<string[]>(() => readRecent(userId))
  // Emoji sob o mouse ou com o foco: o nome aparece no rodapé, no lugar do title nativo (UI-05).
  const [pointed, setPointed] = useState<EmojiItem | null>(null)

  useEffect(() => {
    let active = true
    void loadEmojis(i18n.language).then((list) => {
      if (active) {
        setEmojis(list)
      }
    })
    return () => {
      active = false
    }
  }, [i18n.language])

  const byUnicode = useMemo(
    () => new Map((emojis ?? []).map((emoji) => [emoji.unicode, emoji])),
    [emojis]
  )
  const shownGroup = group === "recent" && recent.length === 0 ? 0 : group
  const visible: EmojiItem[] =
    emojis === null
      ? []
      : query.trim() !== ""
        ? searchEmojis(emojis, query)
        : shownGroup === "recent"
          ? recent.map(
              (unicode) =>
                byUnicode.get(unicode) ?? {
                  unicode,
                  label: unicode,
                  tags: [],
                  group: -1,
                  order: 0,
                }
            )
          : emojis.filter((emoji) => emoji.group === shownGroup)

  function choose(emoji: string) {
    setRecent(pushRecent(userId, emoji))
    onSelect(emoji)
  }

  const heading =
    query.trim() !== ""
      ? t("emoji.results")
      : shownGroup === "recent"
        ? t("emoji.recent")
        : t(`emoji.group.${shownGroup}`)

  return (
    <div className="flex w-[min(20rem,calc(100vw-3rem))] flex-col gap-2">
      <SearchInput
        aria-label={t("emoji.search")}
        placeholder={t("emoji.search")}
        value={query}
        onValueChange={setQuery}
        autoFocus
      />
      {query.trim() === "" ? (
        <div
          role="tablist"
          aria-label={t("emoji.categories")}
          // As dez categorias numa grade que cabe na largura do seletor: nada cortado na borda nem rolagem de lado.
          className="grid grid-cols-10 gap-0.5"
        >
          {(["recent", ...GROUPS] as const).map((key) => {
            const name =
              key === "recent" ? t("emoji.recent") : t(`emoji.group.${key}`)
            return (
              <Tooltip key={key}>
                <TooltipTrigger
                  render={
                    <button
                      type="button"
                      role="tab"
                      aria-selected={shownGroup === key}
                      aria-label={name}
                      disabled={key === "recent" && recent.length === 0}
                      onClick={() => setGroup(key)}
                      className={cn(
                        "flex aspect-square w-full items-center justify-center rounded-md text-lg outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-40",
                        shownGroup === key && "bg-muted"
                      )}
                    />
                  }
                >
                  <span aria-hidden="true">
                    {key === "recent" ? "🕘" : GROUP_ICONS[key]}
                  </span>
                </TooltipTrigger>
                <TooltipContent>{name}</TooltipContent>
              </Tooltip>
            )
          })}
        </div>
      ) : null}
      <p
        className="text-xs font-medium text-muted-foreground"
        aria-live="polite"
      >
        {heading}
      </p>
      {emojis === null ? (
        <Skeleton className="h-56 w-full" />
      ) : visible.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">
          {t("emoji.noResults")}
        </p>
      ) : (
        <div
          className="grid h-56 grid-cols-8 content-start gap-0.5 overflow-y-auto"
          onPointerLeave={() => setPointed(null)}
        >
          {visible.map((emoji) => (
            <button
              key={emoji.unicode}
              type="button"
              aria-label={emoji.label}
              onClick={() => choose(emoji.unicode)}
              onPointerEnter={() => setPointed(emoji)}
              onFocus={() => setPointed(emoji)}
              className="flex aspect-square items-center justify-center rounded-md text-xl outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
            >
              {emoji.unicode}
            </button>
          ))}
        </div>
      )}
      {/* O nome já é o rótulo acessível de cada botão; o rodapé é só visual. */}
      <p
        aria-hidden="true"
        className="flex h-7 min-w-0 items-center gap-2 border-t pt-1 text-sm text-muted-foreground"
      >
        {pointed ? (
          <>
            <span className="text-xl">{pointed.unicode}</span>
            <span className="truncate text-foreground">{pointed.label}</span>
          </>
        ) : (
          t("emoji.hint")
        )}
      </p>
    </div>
  )
}
