import { useTranslation } from "react-i18next"
import { CheckIcon } from "lucide-react"
import { cn } from "cn"

import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { formatDateTime } from "@/lib/format"
import { useVote } from "../api"
import type { Poll } from "../types"

/**
 * Enquete no balão (MS-09): cada opção é um botão que marca ou desmarca o voto, com a barra de resultado, a
 * contagem e quem votou (quando não é anônima).
 */
export function PollView({
  messageId,
  question,
  poll,
  nameOf,
}: {
  messageId: string | null
  question: string
  poll: Poll
  nameOf: (userId: string | null) => string
}) {
  const { t } = useTranslation()
  const vote = useVote()
  const disabled = poll.closed || messageId === null || vote.isPending

  function toggle(optionId: string) {
    if (!messageId) {
      return
    }
    const mine = poll.myVotes.includes(optionId)
    const next = poll.multiple
      ? mine
        ? poll.myVotes.filter((id) => id !== optionId)
        : [...poll.myVotes, optionId]
      : mine
        ? []
        : [optionId]
    vote.mutate(
      { messageId, optionIds: next },
      {
        onError: (error) =>
          toast.add({
            title: errorMessage(error, t("common.genericError")),
            type: "error",
          }),
      }
    )
  }

  return (
    <div
      className="flex min-w-56 flex-col gap-2"
      role="group"
      aria-label={question}
    >
      <p className="font-semibold break-words">{question}</p>
      <p className="text-xs opacity-75">
        {poll.multiple ? t("poll.chooseMany") : t("poll.chooseOne")}
        {poll.anonymous ? ` · ${t("poll.anonymousLabel")}` : ""}
      </p>
      <ul className="flex flex-col gap-1.5">
        {poll.options.map((option) => {
          const mine = poll.myVotes.includes(option.id)
          const share =
            poll.totalVoters === 0 ? 0 : option.votes / poll.totalVoters
          return (
            <li key={option.id}>
              <button
                type="button"
                role={poll.multiple ? "checkbox" : "radio"}
                aria-checked={mine}
                disabled={disabled}
                onClick={(event) => {
                  event.stopPropagation()
                  toggle(option.id)
                }}
                className="flex w-full flex-col gap-1 rounded-md px-1 py-1 text-left outline-none hover:bg-black/5 focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-default dark:hover:bg-white/10"
              >
                <span className="flex items-center gap-2 text-sm">
                  <span
                    aria-hidden="true"
                    className={cn(
                      "flex size-4 shrink-0 items-center justify-center border border-current",
                      poll.multiple ? "rounded-sm" : "rounded-full",
                      mine && "bg-primary text-primary-foreground"
                    )}
                  >
                    {mine ? <CheckIcon className="size-3" /> : null}
                  </span>
                  <span className="flex-1 break-words">{option.text}</span>
                  <span className="text-xs tabular-nums">
                    {t("poll.votes", { count: option.votes })}
                  </span>
                </span>
                <span
                  aria-hidden="true"
                  className="h-1.5 w-full overflow-hidden rounded-full bg-black/10 dark:bg-white/15"
                >
                  <span
                    className="block h-full rounded-full bg-primary transition-[width] motion-reduce:transition-none"
                    style={{ width: `${Math.round(share * 100)}%` }}
                  />
                </span>
                {option.voters.length > 0 ? (
                  <span className="text-xs opacity-75">
                    {option.voters.map(nameOf).join(", ")}
                  </span>
                ) : null}
              </button>
            </li>
          )
        })}
      </ul>
      <p className="text-xs opacity-75" aria-live="polite">
        {t("poll.voters", { count: poll.totalVoters })}
        {poll.closed
          ? ` · ${t("poll.closed")}`
          : poll.closesAt
            ? ` · ${t("poll.closesOn", { when: formatDateTime(poll.closesAt) })}`
            : ""}
      </p>
    </div>
  )
}
