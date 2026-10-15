import { useState } from "react"
import { useNavigate } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { UsersIcon } from "lucide-react"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { groupQueries, useJoinGroup } from "../groups"

/** Prévia do grupo pelo link e o botão de entrar ou pedir para entrar (GR-02). */
export function JoinGroup({ code }: { code: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const preview = useQuery(groupQueries.preview(code))
  const join = useJoinGroup()
  const [requested, setRequested] = useState(false)

  if (preview.isPending) {
    return <Skeleton className="h-48 w-full max-w-md" />
  }
  if (preview.isError) {
    return (
      <Alert variant="destructive" className="max-w-md">
        <AlertDescription>{t("groups.invalidInvite")}</AlertDescription>
      </Alert>
    )
  }
  const group = preview.data
  const open = () =>
    navigate({
      to: "/c/$conversationId",
      params: { conversationId: group.groupId },
    })
  return (
    <Card className="w-full max-w-md">
      <CardHeader className="items-center text-center">
        <UsersIcon aria-hidden="true" className="mx-auto size-10" />
        <CardTitle>
          <h2>{group.name}</h2>
        </CardTitle>
        <CardDescription>
          {t("groups.memberCount", { count: group.memberCount })}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {group.description ? (
          <p className="text-sm break-words whitespace-pre-wrap">
            {group.description}
          </p>
        ) : null}
        {group.member ? (
          <p className="text-sm">{t("groups.alreadyMember")}</p>
        ) : null}
        {group.pending || requested ? (
          <p className="text-sm" role="status">
            {t("groups.pendingApproval")}
          </p>
        ) : null}
      </CardContent>
      <CardFooter className="justify-center">
        {group.member ? (
          <Button onClick={() => void open()}>{t("groups.openGroup")}</Button>
        ) : group.pending || requested ? null : (
          <Button
            disabled={join.isPending}
            onClick={async () => {
              try {
                const result = await join.mutateAsync(code)
                if (result.pending) {
                  setRequested(true)
                } else {
                  await open()
                }
              } catch (error) {
                toast.add({
                  title: errorMessage(error, t("common.genericError")),
                  type: "error",
                })
              }
            }}
          >
            {join.isPending ? <Spinner data-icon="inline-start" /> : null}
            {group.joinApproval ? t("groups.requestJoin") : t("groups.join")}
          </Button>
        )}
      </CardFooter>
    </Card>
  )
}
