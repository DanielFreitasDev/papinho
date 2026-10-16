import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  ArchiveIcon,
  ArchiveRestoreIcon,
  BellIcon,
  BellOffIcon,
  EllipsisVerticalIcon,
  EraserIcon,
  FolderIcon,
  HeartIcon,
  HeartOffIcon,
  MailIcon,
  PinIcon,
  PinOffIcon,
  Trash2Icon,
} from "lucide-react"

import { ConfirmDialog } from "@/components/confirm-dialog"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { useConversationAction } from "../api"
import {
  isMuted,
  organizationQueries,
  useListAction,
  useToggleFolder,
  type ListAction,
} from "../organization"
import type { Conversation, MuteDuration } from "../types"

const DURATIONS: MuteDuration[] = ["EIGHT_HOURS", "ONE_WEEK", "ALWAYS"]

/**
 * Menu da conversa na lista (GR-05): arquivar, fixar, silenciar, favoritar, listas, marcar como não lida, limpar
 * e apagar. Tudo vale só para mim.
 */
export function ConversationMenu({
  conversation,
  label,
}: {
  conversation: Conversation
  label: string
}) {
  const { t } = useTranslation()
  const action = useListAction()
  const unreadAction = useConversationAction()
  const toggleFolder = useToggleFolder()
  const folders = useQuery(organizationQueries.folders())
  const [muteOpen, setMuteOpen] = useState(false)
  const [clearOpen, setClearOpen] = useState(false)
  const [removeOpen, setRemoveOpen] = useState(false)
  const [keepStarred, setKeepStarred] = useState(true)
  const { archived, pinnedAt, favorite, folderIds } = conversation.organization
  const muted = isMuted(conversation)
  const unread = conversation.unreadCount > 0 || conversation.markedUnread

  function run(input: ListAction, done?: string) {
    action.mutate(
      { conversationId: conversation.id, ...input },
      {
        onSuccess: () => (done ? toast.add({ title: done }) : undefined),
        onError: (error) =>
          toast.add({
            title: errorMessage(error, t("common.genericError")),
            type: "error",
          }),
      }
    )
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button
              variant="ghost"
              size="icon"
              aria-label={t("chat.conversationActions", { name: label })}
            />
          }
        >
          <EllipsisVerticalIcon />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem
            onClick={() =>
              run(
                { action: "archive", on: !archived },
                t(archived ? "organize.unarchived" : "organize.archived")
              )
            }
          >
            {archived ? <ArchiveRestoreIcon /> : <ArchiveIcon />}
            {t(archived ? "organize.unarchive" : "organize.archive")}
          </DropdownMenuItem>
          <DropdownMenuItem
            onClick={() => run({ action: "pin", on: pinnedAt === null })}
          >
            {pinnedAt ? <PinOffIcon /> : <PinIcon />}
            {t(pinnedAt ? "organize.unpin" : "organize.pin")}
          </DropdownMenuItem>
          {muted ? (
            <DropdownMenuItem
              onClick={() =>
                run({ action: "mute", duration: null, mentions: false })
              }
            >
              <BellIcon />
              {t("organize.unmute")}
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem onClick={() => setMuteOpen(true)}>
              <BellOffIcon />
              {t("organize.mute")}
            </DropdownMenuItem>
          )}
          <DropdownMenuItem
            onClick={() => run({ action: "favorite", on: !favorite })}
          >
            {favorite ? <HeartOffIcon /> : <HeartIcon />}
            {t(favorite ? "organize.unfavorite" : "organize.favorite")}
          </DropdownMenuItem>
          {folders.data && folders.data.length > 0 ? (
            <DropdownMenuSub>
              <DropdownMenuSubTrigger>
                <FolderIcon />
                {t("organize.lists")}
              </DropdownMenuSubTrigger>
              <DropdownMenuSubContent>
                {folders.data.map((folder) => (
                  <DropdownMenuCheckboxItem
                    key={folder.id}
                    checked={folderIds.includes(folder.id)}
                    onCheckedChange={(checked) =>
                      toggleFolder.mutate({
                        folderId: folder.id,
                        conversationId: conversation.id,
                        on: checked,
                      })
                    }
                  >
                    {folder.name}
                  </DropdownMenuCheckboxItem>
                ))}
              </DropdownMenuSubContent>
            </DropdownMenuSub>
          ) : null}
          {conversation.state === "ACTIVE" ? (
            <DropdownMenuItem
              disabled={unread}
              onClick={() =>
                unreadAction.mutate({
                  conversationId: conversation.id,
                  action: "unread",
                })
              }
            >
              <MailIcon />
              {t("chat.markUnread")}
            </DropdownMenuItem>
          ) : null}
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => setClearOpen(true)}>
            <EraserIcon />
            {t("organize.clear")}
          </DropdownMenuItem>
          <DropdownMenuItem
            variant="destructive"
            onClick={() => setRemoveOpen(true)}
          >
            <Trash2Icon />
            {t("organize.remove")}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      {muteOpen ? (
        <MuteDialog
          name={label}
          onOpenChange={setMuteOpen}
          onConfirm={(duration, mentions) =>
            run(
              { action: "mute", duration, mentions },
              t("organize.mutedToast")
            )
          }
        />
      ) : null}
      <ConfirmDialog
        open={clearOpen}
        onOpenChange={setClearOpen}
        title={t("organize.clearTitle", { name: label })}
        description={t("organize.clearDescription")}
        confirmLabel={t("organize.clear")}
        onConfirm={() =>
          run({ action: "clear", keepStarred }, t("organize.cleared"))
        }
      >
        <Label className="flex items-center gap-2 text-sm font-normal">
          <Checkbox
            checked={keepStarred}
            onCheckedChange={(checked) => setKeepStarred(checked)}
          />
          {t("organize.keepStarred")}
        </Label>
      </ConfirmDialog>
      <ConfirmDialog
        open={removeOpen}
        onOpenChange={setRemoveOpen}
        title={t("organize.removeTitle", { name: label })}
        description={
          conversation.kind === "GROUP"
            ? t("organize.removeGroupDescription")
            : t("organize.removeDescription")
        }
        confirmLabel={t("organize.remove")}
        onConfirm={() => run({ action: "remove" }, t("organize.removed"))}
      />
    </>
  )
}

/** Silenciar por 8 horas, 1 semana ou sempre, com a opção de ainda notificar menções. */
function MuteDialog({
  name,
  onOpenChange,
  onConfirm,
}: {
  name: string
  onOpenChange: (open: boolean) => void
  onConfirm: (duration: MuteDuration, mentions: boolean) => void
}) {
  const { t } = useTranslation()
  const [duration, setDuration] = useState<MuteDuration>("EIGHT_HOURS")
  const [mentions, setMentions] = useState(false)
  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("organize.muteTitle", { name })}</DialogTitle>
          <DialogDescription>{t("organize.muteDescription")}</DialogDescription>
        </DialogHeader>
        <RadioGroup
          value={duration}
          onValueChange={(value) => setDuration(value as MuteDuration)}
          aria-label={t("organize.muteFor")}
        >
          {DURATIONS.map((value) => (
            <Label
              key={value}
              className="flex min-h-11 items-center gap-3 text-sm font-normal"
            >
              <RadioGroupItem value={value} />
              {t(`organize.duration.${value}`)}
            </Label>
          ))}
        </RadioGroup>
        <Label className="flex min-h-11 items-center gap-3 text-sm font-normal">
          <Checkbox
            checked={mentions}
            onCheckedChange={(checked) => setMentions(checked)}
          />
          {t("organize.muteMentions")}
        </Label>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t("common.cancel")}
          </Button>
          <Button
            onClick={() => {
              onConfirm(duration, mentions)
              onOpenChange(false)
            }}
          >
            {t("organize.mute")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
