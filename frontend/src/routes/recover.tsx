import { createFileRoute, Link, useNavigate } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { toast } from "@/components/ui/toast"
import { AuthCard, RecoverForm, authQueries } from "@/features/auth"

export const Route = createFileRoute("/recover")({
  component: RecoverPage,
})

function RecoverPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const instance = useQuery(authQueries.instance())
  return (
    <AuthCard
      title={t("auth.recoverTitle")}
      description={t("auth.recoverDescription")}
    >
      <RecoverForm
        onDone={() => {
          toast.add({ title: t("auth.recovered"), type: "success" })
          void navigate({ to: "/login" })
        }}
      />
      {instance.data?.emailRecovery ? (
        <Link
          to="/forgot-password"
          className="mt-4 block text-center text-sm underline-offset-4 hover:underline"
        >
          {t("auth.useEmail")}
        </Link>
      ) : null}
    </AuthCard>
  )
}
