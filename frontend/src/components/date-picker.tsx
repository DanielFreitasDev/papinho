import { lazy, Suspense, useState } from "react"
import { useTranslation } from "react-i18next"
import { CalendarIcon, XIcon } from "lucide-react"
import { cn } from "cn"

import { Button } from "@/components/ui/button"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"

const DatePickerCalendar = lazy(() => import("./date-picker-calendar"))

/*
 * Data e hora com a aparência do projeto (UI-05), no lugar de <input type="date|datetime-local">. Os valores
 * seguem o formato dos campos nativos, em hora local: dia "AAAA-MM-DD" e data com hora "AAAA-MM-DDTHH:mm".
 */

const pad = (value: number) => String(value).padStart(2, "0")

/** "AAAA-MM-DD" de uma data, no horário local. */
export function toLocalDay(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** "AAAA-MM-DDTHH:mm" de uma data, no horário local (o mesmo valor de um datetime-local). */
export function toLocalDateTime(date: Date): string {
  return `${toLocalDay(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function fromLocalDay(day: string): Date {
  const [year, month, date] = day.split("-").map(Number)
  return new Date(year, month - 1, date)
}

const HOURS = Array.from({ length: 24 }, (_, hour) => pad(hour))
const MINUTE_STEP = 5

type DatePickerProps = {
  id?: string
  /** Dia escolhido ("AAAA-MM-DD") ou null. */
  value: string | null
  onChange: (value: string | null) => void
  /** Nome acessível do botão; o dia escolhido é acrescentado a ele. */
  label: string
  /** Primeiro dia permitido ("AAAA-MM-DD"). */
  min?: string
  /** Mostra o botão de limpar quando há um dia escolhido. */
  clearable?: boolean
  disabled?: boolean
  size?: "sm" | "default"
  className?: string
}

/** Dia escolhido num calendário dentro de um popover (UI-05). */
export function DatePicker({
  id,
  value,
  onChange,
  label,
  min,
  clearable = false,
  disabled,
  size = "default",
  className,
}: DatePickerProps) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const selected = value ? fromLocalDay(value) : undefined
  const shown = selected
    ? new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium" }).format(
        selected
      )
    : null
  return (
    <span className={cn("inline-flex items-center gap-1", className)}>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger
          render={
            <Button
              id={id}
              type="button"
              variant="outline"
              size={size}
              disabled={disabled}
              aria-label={`${label}: ${shown ?? t("dateTime.chooseDate")}`}
              className={cn(
                "justify-start font-normal",
                !shown && "text-muted-foreground"
              )}
            />
          }
        >
          <CalendarIcon data-icon="inline-start" />
          {shown ?? t("dateTime.chooseDate")}
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0" align="start">
          <Suspense fallback={<Skeleton className="m-2 h-64 w-60" />}>
            <DatePickerCalendar
              language={i18n.language}
              selected={selected}
              min={min ? fromLocalDay(min) : undefined}
              onSelect={(date) => {
                if (date) {
                  onChange(toLocalDay(date))
                }
                setOpen(false)
              }}
            />
          </Suspense>
        </PopoverContent>
      </Popover>
      {clearable && value ? (
        <Button
          type="button"
          variant="ghost"
          size={size === "sm" ? "icon-sm" : "icon"}
          aria-label={t("dateTime.clearDate")}
          disabled={disabled}
          onClick={() => onChange(null)}
        >
          <XIcon />
        </Button>
      ) : null}
    </span>
  )
}

type DateTimePickerProps = {
  /** Vai para o botão do dia, para o FieldLabel apontar para ele. */
  id?: string
  /** "AAAA-MM-DDTHH:mm" em hora local, ou "" quando vazio. */
  value: string
  onChange: (value: string) => void
  /** Nome acessível do grupo (o mesmo texto do rótulo visível). */
  label: string
  /** Primeiro momento permitido, no mesmo formato; os dias anteriores ficam desabilitados. */
  min?: string
  /** Mostra o botão de limpar quando há valor (campos opcionais). */
  clearable?: boolean
}

/**
 * Data e hora (UI-05): o dia no calendário e a hora e os minutos (de 5 em 5) em listas próprias. Escolher o
 * dia de um campo vazio usa a hora padrão de 12:00; a hora só fica ativa depois do dia.
 */
export function DateTimePicker({
  id,
  value,
  onChange,
  label,
  min,
  clearable = false,
}: DateTimePickerProps) {
  const { t } = useTranslation()
  const [day, time] = value ? value.split("T") : [null, null]
  const [hour, minute] = time ? time.split(":") : [null, null]
  const minutes = Array.from({ length: 60 / MINUTE_STEP }, (_, index) =>
    pad(index * MINUTE_STEP)
  )
  // Um valor antigo fora do passo (vindo do servidor) continua aparecendo como está.
  if (minute && !minutes.includes(minute)) {
    minutes.push(minute)
    minutes.sort()
  }
  const set = (nextDay: string, nextHour: string, nextMinute: string) =>
    onChange(`${nextDay}T${nextHour}:${nextMinute}`)
  return (
    <div
      role="group"
      aria-label={label}
      className="flex flex-wrap items-center gap-2"
    >
      <DatePicker
        id={id}
        value={day}
        label={t("dateTime.date")}
        min={min?.slice(0, 10)}
        onChange={(next) =>
          next ? set(next, hour ?? "12", minute ?? "00") : onChange("")
        }
      />
      <span className="inline-flex items-center gap-1">
        <Select
          items={HOURS.map((item) => ({ value: item, label: item }))}
          value={hour}
          disabled={!day}
          onValueChange={(next) =>
            day && next && set(day, next, minute ?? "00")
          }
        >
          <SelectTrigger aria-label={t("dateTime.hour")} className="w-[4.5rem]">
            <SelectValue placeholder="--" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {HOURS.map((item) => (
                <SelectItem key={item} value={item}>
                  {item}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
        <span aria-hidden="true">:</span>
        <Select
          items={minutes.map((item) => ({ value: item, label: item }))}
          value={minute}
          disabled={!day}
          onValueChange={(next) => day && next && set(day, hour ?? "12", next)}
        >
          <SelectTrigger
            aria-label={t("dateTime.minute")}
            className="w-[4.5rem]"
          >
            <SelectValue placeholder="--" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {minutes.map((item) => (
                <SelectItem key={item} value={item}>
                  {item}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </span>
      {clearable && value ? (
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t("dateTime.clear")}
          onClick={() => onChange("")}
        >
          <XIcon />
        </Button>
      ) : null}
    </div>
  )
}
