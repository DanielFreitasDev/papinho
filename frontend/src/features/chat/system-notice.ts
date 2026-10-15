import type { TFunction } from "i18next"

import type { SystemNotice } from "./types"

/** Lê o JSON de um aviso do sistema; corpo estranho vira nulo (nunca HTML). */
export function parseNotice(body: string): SystemNotice | null {
  try {
    const value = JSON.parse(body) as Partial<SystemNotice>
    if (typeof value.type !== "string" || !value.actor) {
      return null
    }
    return {
      type: value.type,
      actor: value.actor,
      targets: Array.isArray(value.targets) ? value.targets : [],
      value: typeof value.value === "string" ? value.value : null,
    }
  } catch {
    return null
  }
}

/** Frase traduzida do aviso ("Você adicionou Bruno e Carla"). */
export function describeNotice(
  body: string,
  meId: string,
  t: TFunction
): string {
  const notice = parseNotice(body)
  if (!notice) {
    return t("chat.notice.unknown")
  }
  const name = (me: string) => (person: { id: string; name: string }) =>
    person.id === meId ? me : person.name
  const actor = name(t("chat.you"))(notice.actor)
  const targets = new Intl.ListFormat(t("chat.listLocale"), {
    type: "conjunction",
  }).format(notice.targets.map(name(t("chat.youObject"))))
  const type =
    notice.type === "group.photo" && notice.value === "removed"
      ? "photoRemoved"
      : notice.type.replace("group.", "")
  return t(`chat.notice.${type}`, {
    actor,
    targets,
    value: notice.value ?? "",
    defaultValue: t("chat.notice.unknown"),
  })
}
