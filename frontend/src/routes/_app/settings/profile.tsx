import { createFileRoute } from "@tanstack/react-router"

import { ProfileSettings } from "@/features/account"

export const Route = createFileRoute("/_app/settings/profile")({
  component: ProfileSettings,
})
