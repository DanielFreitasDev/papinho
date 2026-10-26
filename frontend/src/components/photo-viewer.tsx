import { useState, type ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { XIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog"

/**
 * Foto de perfil (pessoa ou grupo) que amplia ao clicar, como no WhatsApp: a imagem em tamanho real sobre fundo
 * escuro; Esc, o X ou um clique fora fecham. Sem foto, mostra só o avatar, sem botão.
 */
export function PhotoViewer({
  src,
  name,
  children,
}: {
  /** URL da foto em tamanho real; nula quando não há foto visível. */
  src: string | null
  name: string
  /** O avatar que abre a foto. */
  children: ReactNode
}) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  if (!src) {
    return children
  }
  return (
    <>
      <button
        type="button"
        className="cursor-zoom-in rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        aria-label={t("profile.viewPhoto", { name })}
        onClick={() => setOpen(true)}
      >
        {children}
      </button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          showCloseButton={false}
          className="flex h-dvh max-h-dvh w-screen max-w-none flex-col gap-0 rounded-none border-0 bg-black/90 p-0 text-white sm:max-w-none"
        >
          <div className="flex items-center gap-1 p-2 pt-[max(0.5rem,env(safe-area-inset-top))]">
            <DialogTitle className="min-w-0 flex-1 truncate px-2 text-sm font-medium text-white">
              {name}
            </DialogTitle>
            <Button
              variant="ghost"
              size="icon"
              className="text-white hover:bg-white/10 hover:text-white"
              aria-label={t("common.close")}
              onClick={() => setOpen(false)}
            >
              <XIcon />
            </Button>
          </div>
          <div
            className="flex min-h-0 flex-1 items-center justify-center p-(--spacing-gutter) pb-[max(var(--spacing-gutter),env(safe-area-inset-bottom))]"
            onClick={(event) => {
              if (event.target === event.currentTarget) {
                setOpen(false)
              }
            }}
          >
            <img
              src={src}
              alt={t("profile.photo")}
              className="aspect-square h-auto max-h-full w-auto max-w-full object-contain select-none"
            />
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
