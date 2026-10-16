import { createFileRoute } from "@tanstack/react-router"

import { LockedFolder } from "@/features/chat"

/** Pasta das conversas trancadas (PV-03): pede o código antes de listar. */
export const Route = createFileRoute("/_app/locked")({
  component: LockedPage,
})

function LockedPage() {
  const { me } = Route.useRouteContext()
  return <LockedFolder meId={me.account.id} />
}
