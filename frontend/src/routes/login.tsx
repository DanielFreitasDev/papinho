import { createFileRoute, redirect, useNavigate } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { z } from "@/lib/zod"

import { AuthCard, LoginForm, authQueries } from "@/features/auth"

const searchSchema = z.object({ redirect: z.string().optional() })

/** Só aceita voltar para caminhos do próprio app (evita redirecionamento aberto). */
function safeRedirect(target: string | undefined): string {
  return target && target.startsWith("/") && !target.startsWith("//")
    ? target
    : "/"
}

export const Route = createFileRoute("/login")({
  validateSearch: searchSchema,
  beforeLoad: async ({ context, search }) => {
    const [me, instance] = await Promise.all([
      context.queryClient.ensureQueryData(authQueries.me()),
      context.queryClient.ensureQueryData(authQueries.instance()),
    ])
    if (me) {
      throw redirect({ to: safeRedirect(search.redirect) })
    }
    if (instance.setupRequired) {
      throw redirect({ to: "/register" })
    }
  },
  component: LoginPage,
})

function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const search = Route.useSearch()
  const instance = useQuery(authQueries.instance())
  return (
    <AuthCard
      title={t("auth.loginTitle")}
      description={t("auth.loginDescription")}
    >
      <LoginForm
        canRegister={instance.data?.registrationMode === "OPEN"}
        onSuccess={() => void navigate({ to: safeRedirect(search.redirect) })}
      />
    </AuthCard>
  )
}
