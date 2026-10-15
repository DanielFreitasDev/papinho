import {
  createFileRoute,
  Link,
  redirect,
  useNavigate,
} from "@tanstack/react-router"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { z } from "@/lib/zod"
import { MailIcon } from "lucide-react"

import { buttonVariants } from "@/components/ui/button"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { AuthCard, RegisterForm, authQueries } from "@/features/auth"
import { checkInvite } from "@/features/auth/api"

const searchSchema = z.object({ invite: z.string().max(128).optional() })

export const Route = createFileRoute("/register")({
  validateSearch: searchSchema,
  beforeLoad: async ({ context }) => {
    const me = await context.queryClient.ensureQueryData(authQueries.me())
    if (me) {
      throw redirect({ to: "/" })
    }
    await context.queryClient.ensureQueryData(authQueries.instance())
  },
  component: RegisterPage,
})

function RegisterPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { invite } = Route.useSearch()
  const instance = useQuery(authQueries.instance())
  const inviteStatus = useQuery({
    queryKey: ["invite-status", invite],
    queryFn: () => checkInvite(invite ?? ""),
    enabled: !!invite,
    retry: false,
  })

  const queryClient = useQueryClient()
  const done = () => {
    void queryClient.invalidateQueries({
      queryKey: authQueries.instance().queryKey,
    })
    void navigate({ to: "/" })
  }
  const data = instance.data
  if (!data) {
    return null
  }

  let blocked: { title: string; description: string } | null = null
  if (!data.setupRequired) {
    if (data.registrationMode === "CLOSED" && !invite) {
      blocked = {
        title: t("auth.registrationClosedTitle"),
        description: t("auth.registrationClosedDescription"),
      }
    } else if (data.registrationMode === "INVITE_ONLY" && !invite) {
      blocked = {
        title: t("auth.inviteRequiredTitle"),
        description: t("auth.inviteRequiredDescription"),
      }
    } else if (invite && inviteStatus.data === false) {
      blocked = {
        title: t("auth.inviteInvalidTitle"),
        description: t("auth.inviteInvalidDescription"),
      }
    }
  }

  if (invite && inviteStatus.isPending) {
    return (
      <AuthCard title={t("auth.registerTitle")}>
        <Skeleton
          role="status"
          className="h-72 w-full"
          aria-label={t("common.loading")}
        />
      </AuthCard>
    )
  }

  if (blocked) {
    return (
      <AuthCard title={t("auth.registerTitle")}>
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <MailIcon />
            </EmptyMedia>
            <EmptyTitle>{blocked.title}</EmptyTitle>
            <EmptyDescription>{blocked.description}</EmptyDescription>
          </EmptyHeader>
          <EmptyContent>
            <Link
              to="/login"
              className={buttonVariants({ variant: "outline" })}
            >
              {t("auth.backToLogin")}
            </Link>
          </EmptyContent>
        </Empty>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title={t("auth.registerTitle")}
      description={t("auth.registerDescription")}
    >
      <RegisterForm
        inviteToken={invite}
        firstAdmin={data.setupRequired}
        onDone={done}
      />
    </AuthCard>
  )
}
