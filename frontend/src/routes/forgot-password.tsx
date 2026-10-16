import { createFileRoute, redirect } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { AuthCard, ForgotPasswordForm, authQueries } from "@/features/auth"

export const Route = createFileRoute("/forgot-password")({
  beforeLoad: async ({ context }) => {
    const instance = await context.queryClient.ensureQueryData(
      authQueries.instance()
    )
    if (!instance.emailRecovery) {
      throw redirect({ to: "/recover" })
    }
  },
  component: ForgotPasswordPage,
})

function ForgotPasswordPage() {
  const { t } = useTranslation()
  return (
    <AuthCard
      title={t("auth.forgotTitle")}
      description={t("auth.forgotDescription")}
    >
      <ForgotPasswordForm />
    </AuthCard>
  )
}
