import { createFileRoute, Link } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Spinner } from "@/components/ui/spinner"
import { AuthCard, tokenFromHash, verifyEmail } from "@/features/auth"
import { useHashToken } from "@/lib/use-hash-token"

export const Route = createFileRoute("/verify-email")({
  component: VerifyEmailPage,
})

/** Confirma o e-mail de recuperação pelo link (ID-05); funciona sem sessão, em qualquer aparelho. */
function VerifyEmailPage() {
  const { t } = useTranslation()
  const token = useHashToken(tokenFromHash)
  // Query (e não efeito) para o StrictMode não gastar o link de uso único com uma segunda chamada.
  const verification = useQuery({
    queryKey: ["auth", "verify-email", token],
    queryFn: async () => {
      await verifyEmail(token ?? "")
      return true
    },
    enabled: token !== null,
    retry: false,
    staleTime: Infinity,
    gcTime: Infinity,
  })
  const failed = token === null || verification.isError
  return (
    <AuthCard
      title={t("auth.verifyTitle")}
      description={t("auth.verifyDescription")}
    >
      <div className="flex flex-col gap-4">
        {failed ? (
          <Alert variant="destructive" role="alert">
            <AlertDescription>{t("auth.linkInvalid")}</AlertDescription>
          </Alert>
        ) : verification.isSuccess ? (
          <Alert role="status">
            <AlertDescription>{t("auth.emailVerified")}</AlertDescription>
          </Alert>
        ) : (
          <p
            role="status"
            className="flex items-center gap-2 text-sm text-muted-foreground"
          >
            <Spinner role="none" aria-hidden="true" aria-label={undefined} />
            {t("common.loading")}
          </p>
        )}
        <Link
          to="/"
          className="text-center text-sm underline-offset-4 hover:underline"
        >
          {t("auth.openApp")}
        </Link>
      </div>
    </AuthCard>
  )
}
