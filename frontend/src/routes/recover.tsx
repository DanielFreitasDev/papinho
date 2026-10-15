import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { toast } from "@/components/ui/toast"
import { AuthCard, RecoverForm } from "@/features/auth"

export const Route = createFileRoute("/recover")({
  component: RecoverPage,
})

function RecoverPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
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
    </AuthCard>
  )
}
