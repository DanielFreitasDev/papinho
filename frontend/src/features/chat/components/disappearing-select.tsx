import { useTranslation } from "react-i18next"

import { toast } from "@/components/ui/toast"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { errorMessage } from "@/lib/api-client"
import { useDisappearing } from "../api"
import type { Conversation } from "../types"

/** Timers aceitos pelo servidor (MS-07): 24 horas, 7 dias, 90 dias. */
export const TIMERS = [86_400, 604_800, 7_776_000] as const

/** Escolha do timer de mensagens temporárias: desligado, 24 h, 7 dias ou 90 dias. */
export function DisappearingSelect({
  id,
  value,
  onChange,
  disabled,
}: {
  id: string
  value: number | null
  onChange: (seconds: number | null) => void
  disabled?: boolean
}) {
  const { t } = useTranslation()
  const items = [
    { value: "off", label: t("chat.timer.off") },
    ...TIMERS.map((seconds) => ({
      value: String(seconds),
      label: t(`chat.timer.${seconds}`),
    })),
  ]
  return (
    <Select
      items={items}
      value={value === null ? "off" : String(value)}
      disabled={disabled}
      onValueChange={(next) =>
        onChange(next === "off" || next === null ? null : Number(next))
      }
    >
      <SelectTrigger id={id} className="w-full">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          {items.map((item) => (
            <SelectItem key={item.value} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}

/** Campo "Mensagens temporárias" dos painéis de contato e de grupo. */
export function DisappearingSetting({
  conversation,
  disabled,
}: {
  conversation: Conversation
  disabled?: boolean
}) {
  const { t } = useTranslation()
  const change = useDisappearing(conversation.id)
  const id = `disappearing-${conversation.id}`
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-xs font-medium text-muted-foreground">
        {t("chat.timer.title")}
      </label>
      <DisappearingSelect
        id={id}
        value={conversation.disappearingSeconds}
        disabled={disabled || change.isPending}
        onChange={(seconds) =>
          change.mutate(seconds, {
            onError: (error) =>
              toast.add({
                title: errorMessage(error, t("common.genericError")),
                type: "error",
              }),
          })
        }
      />
      <p className="text-xs text-muted-foreground">{t("chat.timer.hint")}</p>
    </div>
  )
}
