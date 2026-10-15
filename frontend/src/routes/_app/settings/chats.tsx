import { createFileRoute } from "@tanstack/react-router"

import { ChatSettings } from "@/features/chat"

export const Route = createFileRoute("/_app/settings/chats")({
  component: ChatSettings,
})
