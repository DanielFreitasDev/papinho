import { createFileRoute } from "@tanstack/react-router"

import { ConversationView } from "@/features/chat"

/** Conversa individual (Fase 2). No celular ocupa a tela inteira, com "voltar" (UI-01). */
export const Route = createFileRoute("/_app/c/$conversationId")({
  // ?m=<id> abre a conversa já na mensagem (resultado da busca global, GR-06).
  validateSearch: (search: Record<string, unknown>): { m?: string } =>
    typeof search.m === "string" ? { m: search.m } : {},
  component: ConversationPage,
})

function ConversationPage() {
  const { conversationId } = Route.useParams()
  const { me } = Route.useRouteContext()
  const { m } = Route.useSearch()
  return (
    <ConversationView
      key={conversationId}
      conversationId={conversationId}
      meId={me.account.id}
      focusMessageId={m}
    />
  )
}
