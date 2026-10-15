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
