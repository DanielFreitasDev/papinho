import { createFileRoute } from "@tanstack/react-router"

import { AppearanceSettings } from "@/features/account"

export const Route = createFileRoute("/_app/settings/appearance")({
  component: AppearanceSettings,
})
