import type { ReactNode } from "react"
import { Link } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"
import { ArrowLeftIcon } from "lucide-react"

import { buttonVariants } from "@/components/ui/button"

/**
 * Cabeçalho de uma tela da coluna principal. No celular mostra "voltar" para a lista (navegação em pilha,
 * UI-01); no desktop a lista continua visível ao lado e o botão some.
 */
export function PageHeader({
  title,
  actions,
}: {
  title: string
  actions?: ReactNode
}) {
  const { t } = useTranslation()
  return (
    <header className="sticky top-0 z-10 flex min-h-14 items-center gap-2 border-b bg-background/95 px-(--spacing-gutter) pt-[env(safe-area-inset-top)] backdrop-blur">
      <Link
        to="/"
        className={buttonVariants({
          variant: "ghost",
          size: "icon",
          className: "md:hidden",
        })}
        aria-label={t("common.back")}
      >
        <ArrowLeftIcon />
      </Link>
      <h1 className="min-w-0 flex-1 truncate text-lg font-semibold">{title}</h1>
      {actions}
    </header>
  )
}
