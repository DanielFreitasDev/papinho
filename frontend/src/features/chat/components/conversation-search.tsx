import { useEffect, useRef, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { ChevronDownIcon, ChevronUpIcon, XIcon } from "lucide-react"

import { DatePicker } from "@/components/date-picker"
import { SearchInput } from "@/components/search-input"
import { Button } from "@/components/ui/button"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import {
  hasFilters,
  organizationQueries,
  type SearchFilters,
} from "../organization"

const TYPES = ["MEDIA", "LINKS", "DOCUMENTS", "AUDIO"] as const

/**
 * Busca dentro da conversa (GR-06): texto, remetente, dia e tipo, com "anterior" e "próximo" levando à mensagem
 * (Enter vai para a próxima mais antiga, Shift+Enter volta). Esc fecha.
 */
export function ConversationSearch({
  conversationId,
  people,
  onJump,
  onClose,
}: {
  conversationId: string
  /** Quem pode ser escolhido como remetente (eu e a outra pessoa, ou os membros do grupo). */
  people: { userId: string; label: string }[]
  onJump: (messageId: string) => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [query, setQuery] = useState("")
  const [senderId, setSenderId] = useState<string | null>(null)
  const [date, setDate] = useState<string | null>(null)
  const [type, setType] = useState<SearchFilters["type"]>(null)
  const debounced = useDebouncedValue(query, 300)
  const filters: SearchFilters = { query: debounced, senderId, date, type }
  const results = useQuery(
    organizationQueries.inConversation(conversationId, filters)
  )
  // A posição vale para a lista de resultados em que foi escolhida; lista nova volta ao primeiro.
  const [cursor, setCursor] = useState<{ of: unknown; index: number }>({
    of: null,
    index: 0,
  })
  const index = cursor.of === results.data ? cursor.index : 0
  const inputRef = useRef<HTMLInputElement>(null)
  const found = results.data?.messages ?? []

  useEffect(() => inputRef.current?.focus(), [])
  // Resultado novo: vai para a mensagem mais recente que bate.
  useEffect(() => {
    const first = results.data?.messages[0]
    if (first) {
      onJump(first.id)
    }
    // onJump muda a cada desenho da conversa; só os resultados disparam o salto.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [results.data])

  function go(step: number) {
    if (found.length === 0) {
      return
    }
    const next = (index + step + found.length) % found.length
    setCursor({ of: results.data, index: next })
    onJump(found[next].id)
  }

  const senderItems = [
    { value: "any", label: t("search.anySender") },
    ...people.map((person) => ({ value: person.userId, label: person.label })),
  ]
  const typeItems = [
    { value: "any", label: t("search.anyType") },
    ...TYPES.map((value) => ({ value, label: t(`gallery.tab.${value}`) })),
  ]
  const active = hasFilters(filters)
  const status = !active
    ? ""
    : results.isFetching
      ? t("common.loading")
      : results.isError
        ? t("states.errorTitle")
        : found.length === 0
          ? t("search.noResults")
          : t("search.position", {
              current: index + 1,
              total: found.length,
              more: results.data?.hasMore ? "+" : "",
            })

  return (
    <div
      role="search"
      aria-label={t("search.inConversation")}
      className="flex flex-col gap-2 border-b bg-muted/40 px-2 py-2 md:px-4"
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          event.preventDefault()
          onClose()
        }
      }}
    >
      <div className="flex items-center gap-1">
        <SearchInput
          ref={inputRef}
          className="flex-1"
          value={query}
          aria-label={t("search.inConversation")}
          placeholder={t("search.inConversationPlaceholder")}
          onValueChange={setQuery}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault()
              go(event.shiftKey ? -1 : 1)
            }
          }}
        />
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("search.newer")}
          disabled={found.length < 2}
          onClick={() => go(-1)}
        >
          <ChevronUpIcon />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("search.older")}
          disabled={found.length < 2}
          onClick={() => go(1)}
        >
          <ChevronDownIcon />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("search.close")}
          onClick={onClose}
        >
          <XIcon />
        </Button>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Select
          items={senderItems}
          value={senderId ?? "any"}
          onValueChange={(value) =>
            setSenderId(value === "any" || value === null ? null : value)
          }
        >
          <SelectTrigger
            size="sm"
            aria-label={t("search.sender")}
            className="max-w-40"
          >
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {senderItems.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
        <Select
          items={typeItems}
          value={type ?? "any"}
          onValueChange={(value) =>
            setType(
              value === "any" || value === null
                ? null
                : (value as SearchFilters["type"])
            )
          }
        >
          <SelectTrigger
            size="sm"
            aria-label={t("search.type")}
            className="max-w-36"
          >
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {typeItems.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
        <DatePicker
          label={t("search.date")}
          size="sm"
          clearable
          value={date}
          onChange={setDate}
        />
        <p
          role="status"
          aria-live="polite"
          className="ml-auto text-xs text-muted-foreground"
        >
          {status}
        </p>
      </div>
    </div>
  )
}
