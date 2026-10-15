import i18n from "@/lib/i18n"

/** Datas no fuso de negócio (America/Fortaleza) e no idioma da interface; o servidor guarda tudo em UTC. */
export const BUSINESS_TIME_ZONE = "America/Fortaleza"

export function formatDateTime(value: string | Date): string {
  return new Intl.DateTimeFormat(i18n.language, {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: BUSINESS_TIME_ZONE,
  }).format(typeof value === "string" ? new Date(value) : value)
}

export function formatDate(value: string | Date): string {
  return new Intl.DateTimeFormat(i18n.language, {
    dateStyle: "medium",
    timeZone: BUSINESS_TIME_ZONE,
  }).format(typeof value === "string" ? new Date(value) : value)
}

function toDate(value: string | Date): Date {
  return typeof value === "string" ? new Date(value) : value
}

export function formatTime(value: string | Date): string {
  return new Intl.DateTimeFormat(i18n.language, {
    timeStyle: "short",
    timeZone: BUSINESS_TIME_ZONE,
  }).format(toDate(value))
}

/** Dia do calendário (AAAA-MM-DD) no fuso de negócio, para agrupar mensagens por dia. */
export function dayKey(value: string | Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    timeZone: BUSINESS_TIME_ZONE,
  }).format(toDate(value))
}

function daysAgo(value: string | Date, now = new Date()): number {
  const day = Date.parse(`${dayKey(value)}T00:00:00Z`)
  const today = Date.parse(`${dayKey(now)}T00:00:00Z`)
  return Math.round((today - day) / 86_400_000)
}

/** "Hoje", "Ontem" ou a data, para o separador de dias da conversa. */
export function formatDay(value: string | Date, now = new Date()): string {
  const ago = daysAgo(value, now)
  if (ago === 0) {
    return i18n.t("chat.today")
  }
  if (ago === 1) {
    return i18n.t("chat.yesterday")
  }
  return formatDate(value)
}

/** Horário curto para a lista: a hora se for hoje, senão "Ontem" ou a data. */
export function formatListTime(value: string | Date, now = new Date()): string {
  const ago = daysAgo(value, now)
  if (ago === 0) {
    return formatTime(value)
  }
  if (ago === 1) {
    return i18n.t("chat.yesterday")
  }
  return new Intl.DateTimeFormat(i18n.language, {
    dateStyle: "short",
    timeZone: BUSINESS_TIME_ZONE,
  }).format(toDate(value))
}

/** "hoje às 10:30", "ontem às 22:01" ou "em 12/10/2026 às 09:00" (visto por último). */
export function formatWhen(value: string | Date, now = new Date()): string {
  const ago = daysAgo(value, now)
  const time = formatTime(value)
  if (ago === 0) {
    return i18n.t("chat.todayAt", { time })
  }
  if (ago === 1) {
    return i18n.t("chat.yesterdayAt", { time })
  }
  return i18n.t("chat.dateAt", { date: formatDate(value), time })
}
