import { createFileRoute } from "@tanstack/react-router"

import { PrivacySettings } from "@/features/privacy"

export const Route = createFileRoute("/_app/settings/privacy")({
  component: PrivacySettings,
})
