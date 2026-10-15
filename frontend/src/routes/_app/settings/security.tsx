import { createFileRoute } from "@tanstack/react-router"

import { SecuritySettings } from "@/features/account"

export const Route = createFileRoute("/_app/settings/security")({
  component: SecuritySettings,
})
