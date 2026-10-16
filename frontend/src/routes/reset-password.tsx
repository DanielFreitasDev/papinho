import { useState } from "react"
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { toast } from "@/components/ui/toast"
import { AuthCard, ResetPasswordForm, tokenFromHash } from "@/features/auth"
import { useHashToken } from "@/lib/use-hash-token"

export const Route = createFileRoute("/reset-password")({
  component: ResetPasswordPage,
})

function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const token = useHashToken(tokenFromHash)
  const [done, setDone] = useState(false)
  return (
    <AuthCard
      title={t("auth.resetTitle")}
      description={t("auth.resetDescription")}
    >
      {token && !done ? (
        <ResetPasswordForm
          token={token}
          onDone={() => {
            setDone(true)
            toast.add({ title: t("auth.recovered"), type: "success" })
            void navigate({ to: "/login" })
          }}
        />
      ) : (
        <div className="flex flex-col gap-4">
          <Alert variant="destructive" role="alert">
            <AlertDescription>{t("auth.linkInvalid")}</AlertDescription>
          </Alert>
          <Link
            to="/login"
            className="text-center text-sm underline-offset-4 hover:underline"
          >
            {t("auth.backToLogin")}
          </Link>
        </div>
      )}
    </AuthCard>
  )
}
