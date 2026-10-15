import { createFileRoute } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { PageHeader } from "@/components/layout/page-header"
import { ConversationList } from "@/features/chat"

/** Solicitações de mensagem de quem não está nos contatos (CT-02). */
export const Route = createFileRoute("/_app/requests")({
  component: RequestsPage,
})

function RequestsPage() {
  const { t } = useTranslation()
  const { me } = Route.useRouteContext()
  return (
    <>
      <PageHeader title={t("chat.requests")} />
      <div className="p-2 md:p-4">
        <ConversationList meId={me.account.id} requests />
      </div>
    </>
  )
}
