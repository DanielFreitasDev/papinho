import { createFileRoute } from "@tanstack/react-router"

import { AccountSettings } from "@/features/account"

export const Route = createFileRoute("/_app/settings/account")({
  component: AccountSettings,
})
