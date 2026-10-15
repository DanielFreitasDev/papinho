import { createFileRoute } from "@tanstack/react-router"

import { BlockedSettings } from "@/features/account"

export const Route = createFileRoute("/_app/settings/blocked")({
  component: BlockedSettings,
})
