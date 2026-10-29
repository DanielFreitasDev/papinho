import { useEffect, useRef } from "react"
import { useTranslation } from "react-i18next"
import { cn } from "cn"
import { UsersIcon } from "lucide-react"

import { UserAvatar } from "@/components/user-avatar"
import type { MentionCandidate } from "../format/mention"

/** Uma linha da lista: um membro ou o "@todos" (sem `candidate`). */
export type MentionOption = {
  username: string
  candidate: MentionCandidate | null
}

/**
 * Lista de quem mencionar, acima do campo, como a do WhatsApp: "todos" primeiro, depois os membros com foto, nome e
 * @usuario. O foco fica no campo (↑ e ↓ andam, Enter ou Tab escolhem, Esc fecha); o clique não tira o foco dele.
 */
export function MentionList({
  id,
  options,
  active,
  onActive,
  onPick,
}: {
  id: string
  options: MentionOption[]
  active: number
  onActive: (index: number) => void
  onPick: (username: string) => void
}) {
  const { t } = useTranslation()
  const list = useRef<HTMLDivElement>(null)
  useEffect(() => {
    list.current
      ?.querySelector(`#${CSS.escape(`${id}-${active}`)}`)
      ?.scrollIntoView({ block: "nearest" })
  }, [active, id])

  return (
    <div
      ref={list}
      id={id}
      role="listbox"
      aria-label={t("chat.mentionList")}
      className="absolute bottom-full left-(--spacing-gutter) z-20 mb-1 flex max-h-80 w-[min(24rem,calc(100%-2*var(--spacing-gutter)))] animate-in flex-col overflow-y-auto rounded-xl bg-popover p-(--spacing-inset) text-popover-foreground shadow-lg ring-1 ring-foreground/10 duration-(--duration-base) ease-(--ease-enter) fade-in slide-in-from-bottom-2"
    >
      {options.map((option, index) => {
        const label = option.candidate?.label ?? t("chat.mentionEveryone")
        return (
          <div
            key={option.username}
            id={`${id}-${index}`}
            role="option"
            aria-selected={index === active}
            className={cn(
              "flex cursor-pointer items-center gap-(--spacing-icon) rounded-lg px-(--spacing-item) py-2",
              index === active && "bg-accent text-accent-foreground"
            )}
            onMouseDown={(event) => event.preventDefault()}
            onMouseMove={() => index !== active && onActive(index)}
            onClick={() => onPick(option.username)}
          >
            {option.candidate ? (
              <UserAvatar
                userId={option.candidate.userId}
                name={option.candidate.label}
                hasPhoto={option.candidate.hasPhoto}
                version={option.candidate.photoVersion}
                className="size-9 shrink-0"
                privateItem
              />
            ) : (
              <span
                aria-hidden="true"
                className="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground"
              >
                <UsersIcon className="size-5" />
              </span>
            )}
            <span className="flex min-w-0 flex-col">
              <span className="truncate text-sm" data-private="names">
                {label}
              </span>
              <span className="truncate text-xs text-muted-foreground">
                {option.candidate
                  ? `@${option.candidate.username}`
                  : t("chat.mentionEveryoneHint")}
              </span>
            </span>
          </div>
        )
      })}
    </div>
  )
}
