import { useTranslation } from "react-i18next"

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"

const isMac =
  typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform)

/** Lista de atalhos do teclado, aberta com "?" (MS-13). */
export function ShortcutsDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const mod = isMac ? "⌘" : "Ctrl"
  const rows: [string[], string][] = [
    [[mod, "K"], t("shortcuts.openPalette")],
    [["?"], t("shortcuts.showList")],
    [["Esc"], t("shortcuts.close")],
    [["↑"], t("shortcuts.editLast")],
    [["Enter"], t("shortcuts.send")],
    [["Shift", "Enter"], t("shortcuts.newLine")],
    [[mod, "V"], t("shortcuts.pasteImage")],
    [["Alt", "Shift", "P"], t("shortcuts.privateMode")],
  ]
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("shortcuts.title")}</DialogTitle>
          <DialogDescription>{t("shortcuts.dragHint")}</DialogDescription>
        </DialogHeader>
        <dl className="grid grid-cols-[auto_1fr] items-center gap-x-4 gap-y-2 text-sm">
          {rows.map(([keys, label]) => (
            <div key={label} className="contents">
              <dt className="flex gap-1">
                {keys.map((key) => (
                  <kbd
                    key={key}
                    className="rounded border bg-muted px-1.5 py-0.5 font-mono text-xs"
                  >
                    {key}
                  </kbd>
                ))}
              </dt>
              <dd>{label}</dd>
            </div>
          ))}
        </dl>
      </DialogContent>
    </Dialog>
  )
}
