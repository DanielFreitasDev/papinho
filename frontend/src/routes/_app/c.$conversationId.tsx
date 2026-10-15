import { createFileRoute } from "@tanstack/react-router"

import { ConversationView } from "@/features/chat"

/** Conversa individual (Fase 2). No celular ocupa a tela inteira, com "voltar" (UI-01). */
export const Route = createFileRoute("/_app/c/$conversationId")({
  component: ConversationPage,
})

function ConversationPage() {
  const { conversationId } = Route.useParams()
  const { me } = Route.useRouteContext()
  return (
    <ConversationView
      key={conversationId}
      conversationId={conversationId}
      meId={me.account.id}
    />
  )
}
