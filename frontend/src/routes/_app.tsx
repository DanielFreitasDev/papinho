import { createFileRoute, redirect } from "@tanstack/react-router"

import { AppShell } from "@/components/layout/app-shell"
import { authQueries } from "@/features/auth"

/** Rotas autenticadas: sem sessão, vai para o login e volta depois. */
export const Route = createFileRoute("/_app")({
  beforeLoad: async ({ context, location }) => {
    const me = await context.queryClient.ensureQueryData(authQueries.me())
    if (!me) {
      throw redirect({ to: "/login", search: { redirect: location.href } })
    }
    return { me }
  },
  component: AppShell,
})
