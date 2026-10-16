import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { UserPlusIcon, UserXIcon } from "lucide-react"

import { UserAvatar } from "@/components/user-avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { contactQueries, useAddContact } from "../api"

/** Tela aberta pelo link ou QR Code de alguém: mostra o cartão e adiciona aos contatos (CT-01). */
export function PersonCard({ username }: { username: string }) {
  const { t } = useTranslation()
  const person = useQuery(contactQueries.person(username))
  const add = useAddContact()

  if (person.isPending) {
    return (
      <Skeleton
        role="status"
        className="h-48 w-full max-w-sm"
        aria-label={t("common.loading")}
      />
    )
  }
  if (person.isError) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <UserXIcon />
          </EmptyMedia>
          <EmptyTitle>{t("contacts.notFound")}</EmptyTitle>
          <EmptyDescription>@{username}</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }
  const data = person.data
  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="flex flex-row items-center gap-3">
        <UserAvatar
          privateItem
          userId={data.userId}
          name={data.displayName}
          hasPhoto={data.hasPhoto}
          version={data.photoVersion}
          className="size-16"
        />
        <div className="min-w-0">
          <CardTitle>
            <h1 className="truncate text-lg font-semibold">
              {data.displayName}
            </h1>
          </CardTitle>
          <CardDescription>@{data.username}</CardDescription>
        </div>
      </CardHeader>
      {data.about ? <CardContent>{data.about}</CardContent> : null}
      <CardFooter className="flex flex-wrap gap-2">
        {data.self ? (
          <Badge variant="secondary">{t("contacts.thisIsYou")}</Badge>
        ) : data.contact ? (
          <Badge variant="secondary">{t("contacts.alreadyContact")}</Badge>
        ) : (
          <Button
            disabled={add.isPending}
            onClick={async () => {
              await add.mutateAsync({ username: data.username })
              toast.add({ title: t("contacts.added"), type: "success" })
              void person.refetch()
            }}
          >
            {add.isPending ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <UserPlusIcon data-icon="inline-start" />
            )}
            {t("contacts.add")}
          </Button>
        )}
        {data.blocked ? (
          <Badge variant="destructive">{t("contacts.blocked")}</Badge>
        ) : null}
      </CardFooter>
    </Card>
  )
}
