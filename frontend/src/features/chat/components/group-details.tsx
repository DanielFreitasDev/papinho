import { useRef, useState } from "react"
import { useNavigate } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  CameraIcon,
  ChevronRightIcon,
  EllipsisVerticalIcon,
  ImagesIcon,
  LinkIcon,
  LogOutIcon,
  PencilIcon,
  UserPlusIcon,
  XIcon,
} from "lucide-react"

import { ConfirmDialog } from "@/components/confirm-dialog"
import { UserAvatar } from "@/components/user-avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { toast } from "@/components/ui/toast"
import { uploadFile } from "@/features/media"
import { errorMessage } from "@/lib/api-client"
import { svgDataUrl } from "@/lib/image"
import { useOpenDirect } from "../api"
import { groupQueries, useGroupAction } from "../groups"
import type { Attachment, Conversation, GroupInfo } from "../types"
import { ContactPicker } from "./contact-picker"
import { ConversationAvatar } from "./conversation-avatar"
import { ConversationGallery } from "./conversation-gallery"

/**
 * Painel "Dados do grupo" (GR-01, GR-02): foto, nome e descrição, membros com admins, adicionar, convite por
 * link e QR Code, pedidos de entrada, permissões e sair. Os botões seguem as permissões; o servidor confere.
 */
export function GroupDetails({
  conversation,
  group,
  onClose,
  onOpenImage,
}: {
  conversation: Conversation
  group: GroupInfo
  onClose: () => void
  onOpenImage: (images: Attachment[], id: string) => void
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const members = useQuery(groupQueries.members(conversation.id))
  const action = useGroupAction(conversation.id)
  const openDirect = useOpenDirect()
  const admin = group.myRole === "ADMIN"
  const [gallery, setGallery] = useState(false)
  const [editing, setEditing] = useState(false)
  const [adding, setAdding] = useState(false)
  const [inviting, setInviting] = useState(false)
  const [leaving, setLeaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const photoInput = useRef<HTMLInputElement>(null)

  const run = (input: Parameters<typeof action.mutate>[0], done?: string) =>
    action.mutateAsync(input).then(
      () => {
        if (done) {
          toast.add({ title: done })
        }
      },
      (error: unknown) =>
        toast.add({
          title: errorMessage(error, t("common.genericError")),
          type: "error",
        })
    )

  if (gallery) {
    return (
      <ConversationGallery
        conversationId={conversation.id}
        onBack={() => setGallery(false)}
        onOpenImage={onOpenImage}
      />
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex min-h-14 items-center gap-2 border-b px-2">
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("common.close")}
          onClick={onClose}
        >
          <XIcon />
        </Button>
        <h2 className="text-base font-semibold">{t("groups.info")}</h2>
      </div>
      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-4">
        <div className="flex flex-col items-center gap-2 text-center">
          <ConversationAvatar
            conversation={conversation}
            className="size-28 text-2xl"
          />
          {group.canEditInfo ? (
            <div className="flex flex-wrap justify-center gap-2">
              <input
                ref={photoInput}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                hidden
                tabIndex={-1}
                aria-hidden="true"
                onChange={async (event) => {
                  const file = event.target.files?.[0]
                  event.target.value = ""
                  if (!file) {
                    return
                  }
                  setUploading(true)
                  try {
                    const mediaId = await uploadFile(file, () => {}).done
                    await run({ action: "photo", mediaId })
                  } catch (error) {
                    toast.add({
                      title: errorMessage(error, t("common.genericError")),
                      type: "error",
                    })
                  } finally {
                    setUploading(false)
                  }
                }}
              />
              <Button
                variant="outline"
                size="sm"
                disabled={uploading}
                onClick={() => photoInput.current?.click()}
              >
                {uploading ? (
                  <Spinner data-icon="inline-start" />
                ) : (
                  <CameraIcon data-icon="inline-start" />
                )}
                {t("groups.changePhoto")}
              </Button>
              {group.photoId ? (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => void run({ action: "photo", mediaId: null })}
                >
                  {t("groups.removePhoto")}
                </Button>
              ) : null}
            </div>
          ) : null}
          <p className="text-lg font-semibold break-words">{group.name}</p>
          <p className="text-sm text-muted-foreground">
            {t("groups.memberCount", { count: group.memberCount })}
          </p>
          {group.description ? (
            <p className="text-sm break-words whitespace-pre-wrap">
              {group.description}
            </p>
          ) : null}
          {group.canEditInfo ? (
            <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
              <PencilIcon data-icon="inline-start" />
              {t("groups.editInfo")}
            </Button>
          ) : null}
        </div>
        <Separator />
        <Button
          variant="ghost"
          className="justify-start"
          onClick={() => setGallery(true)}
        >
          <ImagesIcon data-icon="inline-start" />
          <span className="flex-1 text-left">{t("gallery.title")}</span>
          <ChevronRightIcon data-icon="inline-end" />
        </Button>
        <Separator />
        <section
          aria-labelledby="group-members"
          className="flex flex-col gap-1"
        >
          <h3
            id="group-members"
            className="text-xs font-medium text-muted-foreground"
          >
            {t("groups.memberCount", { count: group.memberCount })}
          </h3>
          {group.canAddMembers ? (
            <Button
              variant="ghost"
              className="justify-start"
              onClick={() => setAdding(true)}
            >
              <UserPlusIcon data-icon="inline-start" />
              {t("groups.addMembers")}
            </Button>
          ) : null}
          {admin ? (
            <Button
              variant="ghost"
              className="justify-start"
              onClick={() => setInviting(true)}
            >
              <LinkIcon data-icon="inline-start" />
              {t("groups.inviteLink")}
            </Button>
          ) : null}
          {members.isPending ? (
            <Skeleton className="h-10 w-full" />
          ) : (
            <ul className="flex flex-col">
              {members.data?.map((member) => (
                <li
                  key={member.userId}
                  className="flex min-h-12 items-center gap-3 rounded-md px-1"
                >
                  <UserAvatar
                    userId={member.userId}
                    name={member.label}
                    hasPhoto={member.hasPhoto}
                    version={member.photoVersion}
                    className="size-9"
                  />
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="truncate text-sm">
                      {member.me ? t("chat.you") : member.label}
                    </span>
                    <span className="truncate text-xs text-muted-foreground">
                      @{member.username}
                    </span>
                  </span>
                  {member.role === "ADMIN" ? (
                    <Badge variant="secondary">{t("groups.admin")}</Badge>
                  ) : null}
                  {member.me ? null : (
                    <DropdownMenu>
                      <DropdownMenuTrigger
                        render={
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={t("groups.memberActions", {
                              name: member.label,
                            })}
                          />
                        }
                      >
                        <EllipsisVerticalIcon />
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem
                          onClick={async () => {
                            const direct = await openDirect.mutateAsync(
                              member.username
                            )
                            await navigate({
                              to: "/c/$conversationId",
                              params: { conversationId: direct.id },
                            })
                          }}
                        >
                          {t("groups.messageMember", { name: member.label })}
                        </DropdownMenuItem>
                        {admin ? (
                          <>
                            <DropdownMenuItem
                              onClick={() =>
                                void run({
                                  action: "admin",
                                  userId: member.userId,
                                  admin: member.role !== "ADMIN",
                                })
                              }
                            >
                              {member.role === "ADMIN"
                                ? t("groups.dismissAdmin")
                                : t("groups.makeAdmin")}
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              variant="destructive"
                              onClick={() =>
                                void run(
                                  { action: "remove", userId: member.userId },
                                  t("groups.removed", { name: member.label })
                                )
                              }
                            >
                              {t("groups.removeMember", { name: member.label })}
                            </DropdownMenuItem>
                          </>
                        ) : null}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>
        {admin ? (
          <>
            <JoinRequests conversationId={conversation.id} />
            <Separator />
            <GroupSettings
              group={group}
              onChange={(settings) =>
                void run({ action: "settings", ...settings })
              }
            />
          </>
        ) : null}
        <Separator />
        <Button
          variant="ghost"
          className="justify-start text-destructive hover:text-destructive"
          onClick={() => setLeaving(true)}
        >
          <LogOutIcon data-icon="inline-start" />
          {t("groups.leave")}
        </Button>
      </div>
      <EditInfoDialog
        open={editing}
        onOpenChange={setEditing}
        group={group}
        onSave={(name, description) =>
          run({ action: "info", name, description }).then(() =>
            setEditing(false)
          )
        }
      />
      <AddMembersDialog
        open={adding}
        onOpenChange={setAdding}
        exclude={members.data?.map((member) => member.userId) ?? []}
        onAdd={(userIds) =>
          run({ action: "add", userIds }, t("groups.added")).then(() =>
            setAdding(false)
          )
        }
      />
      {inviting ? (
        <InviteDialog
          conversationId={conversation.id}
          onClose={() => setInviting(false)}
        />
      ) : null}
      <ConfirmDialog
        open={leaving}
        onOpenChange={setLeaving}
        title={t("groups.leaveTitle", { name: group.name })}
        description={t("groups.leaveDescription")}
        confirmLabel={t("groups.leave")}
        onConfirm={async () => {
          await run({ action: "leave" }, t("groups.left"))
          await navigate({ to: "/" })
        }}
      />
    </div>
  )
}

function GroupSettings({
  group,
  onChange,
}: {
  group: GroupInfo
  onChange: (settings: {
    onlyAdminsSend: boolean
    onlyAdminsEditInfo: boolean
    onlyAdminsAddMembers: boolean
    joinApproval: boolean
  }) => void
}) {
  const { t } = useTranslation()
  const current = {
    onlyAdminsSend: group.onlyAdminsSend,
    onlyAdminsEditInfo: group.onlyAdminsEditInfo,
    onlyAdminsAddMembers: group.onlyAdminsAddMembers,
    joinApproval: group.joinApproval,
  }
  const toggles = [
    ["onlyAdminsSend", t("groups.onlyAdminsSend")],
    ["onlyAdminsEditInfo", t("groups.onlyAdminsEditInfo")],
    ["onlyAdminsAddMembers", t("groups.onlyAdminsAddMembers")],
    ["joinApproval", t("groups.joinApproval")],
  ] as const
  return (
    <section aria-labelledby="group-settings" className="flex flex-col gap-3">
      <h3
        id="group-settings"
        className="text-xs font-medium text-muted-foreground"
      >
        {t("groups.settings")}
      </h3>
      {toggles.map(([key, label]) => (
        <Field key={key} orientation="horizontal">
          <Switch
            id={`group-${key}`}
            checked={current[key]}
            onCheckedChange={(checked) =>
              onChange({ ...current, [key]: checked })
            }
          />
          <FieldLabel htmlFor={`group-${key}`}>{label}</FieldLabel>
        </Field>
      ))}
    </section>
  )
}

/** Pedidos de entrada pelo link, quando a aprovação está ligada (GR-02). */
function JoinRequests({ conversationId }: { conversationId: string }) {
  const { t } = useTranslation()
  const requests = useQuery(groupQueries.requests(conversationId))
  const action = useGroupAction(conversationId)
  if (!requests.data || requests.data.length === 0) {
    return null
  }
  return (
    <section aria-labelledby="group-requests" className="flex flex-col gap-1">
      <h3
        id="group-requests"
        className="text-xs font-medium text-muted-foreground"
      >
        {t("groups.joinRequests", { count: requests.data.length })}
      </h3>
      <ul className="flex flex-col gap-1">
        {requests.data.map((request) => (
          <li key={request.userId} className="flex items-center gap-2">
            <UserAvatar
              userId={request.userId}
              name={request.label}
              hasPhoto={request.hasPhoto}
              version={request.photoVersion}
              className="size-8"
            />
            <span className="min-w-0 flex-1 truncate text-sm">
              {request.label}
            </span>
            <Button
              size="sm"
              onClick={() =>
                action.mutate({
                  action: "decide",
                  userId: request.userId,
                  approve: true,
                })
              }
            >
              {t("groups.approve")}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() =>
                action.mutate({
                  action: "decide",
                  userId: request.userId,
                  approve: false,
                })
              }
            >
              {t("groups.decline")}
            </Button>
          </li>
        ))}
      </ul>
    </section>
  )
}

function EditInfoDialog({
  open,
  onOpenChange,
  group,
  onSave,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  group: GroupInfo
  onSave: (name: string, description: string) => Promise<void>
}) {
  const { t } = useTranslation()
  const [name, setName] = useState(group.name)
  const [description, setDescription] = useState(group.description)
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (next) {
          setName(group.name)
          setDescription(group.description)
        }
        onOpenChange(next)
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("groups.editInfo")}</DialogTitle>
        </DialogHeader>
        <form
          id="group-info"
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            void onSave(name.trim(), description.trim())
          }}
        >
          <Field>
            <FieldLabel htmlFor="group-info-name">
              {t("groups.name")}
            </FieldLabel>
            <Input
              id="group-info-name"
              required
              maxLength={100}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="group-info-description">
              {t("groups.description")}
            </FieldLabel>
            <Textarea
              id="group-info-description"
              maxLength={2048}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </Field>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" form="group-info" disabled={name.trim() === ""}>
            {t("common.save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function AddMembersDialog({
  open,
  onOpenChange,
  exclude,
  onAdd,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  exclude: string[]
  onAdd: (userIds: string[]) => Promise<void>
}) {
  const { t } = useTranslation()
  const [selected, setSelected] = useState<string[]>([])
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setSelected([])
        onOpenChange(next)
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("groups.addMembers")}</DialogTitle>
        </DialogHeader>
        <ContactPicker
          selected={selected}
          onChange={setSelected}
          exclude={exclude}
        />
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t("common.cancel")}
          </Button>
          <Button
            disabled={selected.length === 0}
            onClick={() => void onAdd(selected).then(() => setSelected([]))}
          >
            {t("groups.add")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Link de convite com QR Code; redefinir invalida o anterior (GR-02). */
function InviteDialog({
  conversationId,
  onClose,
}: {
  conversationId: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const invite = useQuery(groupQueries.invite(conversationId))
  const action = useGroupAction(conversationId)
  return (
    <Dialog open onOpenChange={(open) => (open ? null : onClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("groups.inviteLink")}</DialogTitle>
        </DialogHeader>
        {invite.data ? (
          <div className="flex flex-col items-center gap-3">
            <img
              src={svgDataUrl(invite.data.qrSvg)}
              alt={t("groups.inviteQr")}
              className="size-48 rounded-md bg-white p-2"
            />
            <code className="w-full rounded-md bg-muted p-2 text-xs break-all">
              {invite.data.url}
            </code>
            <p className="text-sm text-muted-foreground">
              {t("groups.inviteHint")}
            </p>
          </div>
        ) : (
          <Skeleton className="h-48 w-full" />
        )}
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() =>
              void action
                .mutateAsync({ action: "revokeInvite" })
                .then(() => onClose())
            }
          >
            {t("groups.revokeInvite")}
          </Button>
          <Button
            variant="outline"
            onClick={() => void action.mutateAsync({ action: "resetInvite" })}
          >
            {t("groups.resetInvite")}
          </Button>
          <Button
            disabled={!invite.data}
            onClick={() =>
              void navigator.clipboard
                ?.writeText(invite.data!.url)
                .then(() => toast.add({ title: t("common.copied") }))
            }
          >
            {t("groups.copyLink")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
