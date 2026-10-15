import { createFileRoute } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"
import { MessagesSquareIcon } from "lucide-react"

import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"

export const Route = createFileRoute("/_app/")({
  component: HomePage,
})

/** Coluna principal sem conversa aberta (só aparece no desktop; no celular a lista ocupa a tela). */
function HomePage() {
  const { t } = useTranslation()
  return (
    <div className="flex flex-1 items-center justify-center p-6">
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <MessagesSquareIcon />
          </EmptyMedia>
          <EmptyTitle>
            <h1>{t("shell.welcomeTitle")}</h1>
          </EmptyTitle>
          <EmptyDescription>{t("shell.welcomeDescription")}</EmptyDescription>
        </EmptyHeader>
      </Empty>
    </div>
  )
}
