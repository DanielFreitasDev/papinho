import { createFileRoute } from "@tanstack/react-router"

import { NotificationSettings } from "@/features/notification"

export const Route = createFileRoute("/_app/settings/notifications")({
  component: NotificationSettings,
})
