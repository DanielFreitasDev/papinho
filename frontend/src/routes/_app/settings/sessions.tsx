import { createFileRoute } from "@tanstack/react-router"

import { SessionsSettings } from "@/features/account"

export const Route = createFileRoute("/_app/settings/sessions")({
  component: SessionsSettings,
})
