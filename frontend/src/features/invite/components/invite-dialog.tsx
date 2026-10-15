import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { CopyIcon, Share2Icon } from "lucide-react"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemGroup,
} from "@/components/ui/item"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { formatDateTime } from "@/lib/format"
import { svgDataUrl } from "@/lib/image"
import {
  inviteQueries,
  useCreateInvite,
  useRevokeInvite,
  type CreatedInvite,
} from "../api"

const VALIDITY_HOURS = [1, 24, 168, 720] as const
const MAX_USES = [1, 5, 20, 100] as const

/** Botão "Convidar amigos" (ID-07): cria convite com validade e limite, mostra link e QR, lista e revoga. */
export function InviteDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const [validHours, setValidHours] = useState<number>(168)
  const [maxUses, setMaxUses] = useState<number>(5)
  const [created, setCreated] = useState<CreatedInvite | null>(null)
  const [error, setError] = useState<string | null>(null)
  const create = useCreateInvite()
  const revoke = useRevokeInvite()
  const invites = useQuery({ ...inviteQueries.list(), enabled: open })

  const validityLabel = (hours: number) =>
    hours < 24
      ? t("invite.hours", { count: hours })
      : t("invite.days", { count: hours / 24 })
  const validityItems = VALIDITY_HOURS.map((hours) => ({
    value: hours,
    label: validityLabel(hours),
  }))
  const usesItems = MAX_USES.map((uses) => ({
    value: uses,
    label: t("invite.usesValue", { count: uses }),
  }))

  async function submit() {
    setError(null)
    try {
      setCreated(await create.mutateAsync({ validHours, maxUses }))
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) setCreated(null)
        onOpenChange(next)
      }}
    >
      <DialogContent className="max-h-[90dvh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t("invite.title")}</DialogTitle>
          <DialogDescription>{t("invite.description")}</DialogDescription>
        </DialogHeader>
        {error ? (
          <Alert variant="destructive" role="alert">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}
        {created ? (
          <div className="flex flex-col items-center gap-3">
            <img
              src={svgDataUrl(created.qrSvg)}
              alt={t("invite.link")}
              className="size-48 rounded-lg bg-white p-2"
            />
            <code className="w-full rounded-md bg-muted p-2 text-center text-sm break-all">
              {created.url}
            </code>
            <div className="flex flex-wrap justify-center gap-2">
              {typeof navigator.share === "function" ? (
                <Button
                  variant="outline"
                  onClick={() =>
                    void navigator
                      .share({ text: t("invite.shareText"), url: created.url })
                      .catch(() => undefined)
                  }
                >
                  <Share2Icon data-icon="inline-start" />
                  {t("common.share")}
                </Button>
              ) : null}
              <Button
                onClick={async () => {
                  await navigator.clipboard.writeText(created.url)
                  toast.add({ title: t("common.copied") })
                }}
              >
                <CopyIcon data-icon="inline-start" />
                {t("common.copy")}
              </Button>
            </div>
          </div>
        ) : (
          <FieldGroup className="sm:flex-row">
            <Field>
              <FieldLabel htmlFor="invite-validity">
                {t("invite.validity")}
              </FieldLabel>
              <Select
                items={validityItems}
                value={validHours}
                onValueChange={(value) => setValidHours(Number(value))}
              >
                <SelectTrigger id="invite-validity" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {validityItems.map((item) => (
                      <SelectItem key={item.value} value={item.value}>
                        {item.label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel htmlFor="invite-uses">{t("invite.uses")}</FieldLabel>
              <Select
                items={usesItems}
                value={maxUses}
                onValueChange={(value) => setMaxUses(Number(value))}
              >
                <SelectTrigger id="invite-uses" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {usesItems.map((item) => (
                      <SelectItem key={item.value} value={item.value}>
                        {item.label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            <Field className="sm:justify-end">
              <Button onClick={submit} disabled={create.isPending}>
                {create.isPending ? <Spinner data-icon="inline-start" /> : null}
                {t("invite.create")}
              </Button>
            </Field>
          </FieldGroup>
        )}
        {invites.data && invites.data.length > 0 ? (
          <>
            <Separator />
            <section
              aria-labelledby="my-invites"
              className="flex flex-col gap-2"
            >
              <h2 id="my-invites" className="text-sm font-medium">
                {t("invite.mine")}
              </h2>
              <ItemGroup>
                {invites.data.map((invite) => (
                  <Item key={invite.id} size="sm" variant="outline">
                    <ItemContent>
                      <ItemDescription>
                        {t("invite.status", {
                          uses: invite.uses,
                          max: invite.maxUses,
                          date: formatDateTime(invite.expiresAt),
                        })}
                      </ItemDescription>
                    </ItemContent>
                    <ItemActions>
                      {invite.usable ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => revoke.mutate(invite.id)}
                          disabled={revoke.isPending}
                        >
                          {t("invite.revoke")}
                        </Button>
                      ) : (
                        <Badge variant="secondary">
                          {invite.revoked
                            ? t("invite.revoked")
                            : t("invite.expired")}
                        </Badge>
                      )}
                    </ItemActions>
                  </Item>
                ))}
              </ItemGroup>
            </section>
          </>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}
