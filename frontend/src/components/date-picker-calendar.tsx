import { enUS, ptBR } from "react-day-picker/locale"

import { Calendar } from "@/components/ui/calendar"

/**
 * Calendário do DatePicker no idioma da interface. Fica num módulo próprio, carregado sob demanda, para o
 * react-day-picker e o date-fns não entrarem no pacote das telas que só abrem o seletor de vez em quando.
 */
export default function DatePickerCalendar({
  language,
  selected,
  min,
  onSelect,
}: {
  language: string
  selected: Date | undefined
  min: Date | undefined
  onSelect: (date: Date | undefined) => void
}) {
  return (
    <Calendar
      mode="single"
      selected={selected}
      defaultMonth={selected}
      locale={language.startsWith("pt") ? ptBR : enUS}
      disabled={min ? { before: min } : undefined}
      autoFocus
      onSelect={onSelect}
    />
  )
}
