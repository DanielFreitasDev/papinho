import { useTranslation } from "react-i18next"

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { isMac, shortcutKeys, type ShortcutName } from "@/lib/shortcuts"

type Row = { keys: string[]; label: string }

/**
 * Lista de atalhos do teclado, aberta com "?" (MS-13), no formato da do WhatsApp: o que faz à esquerda e as teclas à
 * direita, em grupos. Os atalhos do WhatsApp vêm de lib/shortcuts.ts (a mesma tabela que os reconhece).
 */
export function ShortcutsDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const mod = isMac ? "⌘" : "Ctrl"
  const shortcut = (name: ShortcutName): Row => ({
    keys: shortcutKeys(name),
    label: t(`shortcuts.action.${name}`),
  })
  const groups: { title: string; rows: Row[] }[] = [
    {
      title: t("shortcuts.groupMessage"),
      rows: [
        { keys: ["Enter"], label: t("shortcuts.send") },
        { keys: ["Shift", "Enter"], label: t("shortcuts.newLine") },
        { keys: ["↑"], label: t("shortcuts.editLast") },
        { keys: [mod, "V"], label: t("shortcuts.pasteImage") },
        shortcut("emoji"),
      ],
    },
    {
      title: t("shortcuts.groupConversation"),
      rows: [
        shortcut("searchChat"),
        shortcut("markUnread"),
        shortcut("archive"),
        shortcut("pin"),
        { keys: ["Esc"], label: t("shortcuts.close") },
      ],
    },
    {
      title: t("shortcuts.groupApp"),
      rows: [
        { keys: [mod, "K"], label: t("shortcuts.openPalette") },
        shortcut("search"),
        shortcut("newChat"),
        shortcut("newGroup"),
        shortcut("nextChat"),
        shortcut("previousChat"),
        shortcut("profile"),
        shortcut("settings"),
        { keys: ["Alt", "Shift", "P"], label: t("shortcuts.privateMode") },
        { keys: ["?"], label: t("shortcuts.showList") },
      ],
    },
  ]
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t("shortcuts.title")}</DialogTitle>
          <DialogDescription>{t("shortcuts.dragHint")}</DialogDescription>
        </DialogHeader>
        <div className="-mx-1 grid max-h-[65dvh] gap-x-8 gap-y-5 overflow-y-auto px-1 md:grid-cols-2">
          {groups.map((group) => (
            <section key={group.title} className="flex flex-col gap-2">
              <h3 className="text-xs font-medium text-muted-foreground">
                {group.title}
              </h3>
              <dl className="flex flex-col gap-2 text-sm">
                {group.rows.map((row) => (
                  <div
                    key={row.label}
                    className="flex items-center justify-between gap-3"
                  >
                    <dt className="min-w-0">{row.label}</dt>
                    <dd className="flex shrink-0 gap-1">
                      {row.keys.map((key) => (
                        <kbd
                          key={key}
                          className="min-w-6 rounded-md border border-b-2 bg-muted px-1.5 py-0.5 text-center font-mono text-xs"
                        >
                          {key}
                        </kbd>
                      ))}
                    </dd>
                  </div>
                ))}
              </dl>
            </section>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}
