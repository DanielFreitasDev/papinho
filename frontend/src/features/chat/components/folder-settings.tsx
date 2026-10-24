import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react"

import { ConfirmDialog } from "@/components/confirm-dialog"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import { SettingsSection } from "@/features/account"
import { errorMessage } from "@/lib/api-client"
import { chatQueries, useChangePreferences } from "../api"
import {
  organizationQueries,
  useDeleteFolder,
  useSaveFolder,
} from "../organization"
import type { Folder } from "../types"
import { useConversationLabel } from "./conversation-avatar"
import { useErrorToast } from "@/hooks/use-error-toast"

/** Configurações > Conversas: listas personalizadas e "manter arquivadas" (GR-05). */
export function FolderSettings() {
  const { t } = useTranslation()
  const folders = useQuery(organizationQueries.folders())
  const remove = useDeleteFolder()
  const onError = useErrorToast()
  const [editing, setEditing] = useState<Folder | "new" | null>(null)
  const [deleting, setDeleting] = useState<Folder | null>(null)
  return (
    <SettingsSection
      title={t("organize.foldersTitle")}
      description={t("organize.foldersDescription")}
      action={
        <Button size="sm" onClick={() => setEditing("new")}>
          <PlusIcon />
          {t("organize.newFolder")}
        </Button>
      }
    >
      {folders.isPending ? (
        <Skeleton className="h-9 w-full" />
      ) : folders.data?.length ? (
        <ul className="flex flex-col divide-y">
          {folders.data.map((folder) => (
            <li key={folder.id} className="flex items-center gap-2 py-1">
              <span className="flex-1 truncate text-sm font-medium">
                {folder.name}
              </span>
              <span className="text-xs text-muted-foreground">
                {folder.conversationIds.length}
              </span>
              <Button
                variant="ghost"
                size="icon"
                aria-label={t("organize.editFolder", { name: folder.name })}
                onClick={() => setEditing(folder)}
              >
                <PencilIcon />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                aria-label={t("organize.deleteFolder", { name: folder.name })}
                onClick={() => setDeleting(folder)}
              >
                <Trash2Icon />
              </Button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted-foreground">
          {t("organize.noFolders")}
        </p>
      )}
      {editing ? (
        <FolderDialog
          folder={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => (open ? undefined : setDeleting(null))}
        title={t("organize.deleteFolderTitle", { name: deleting?.name ?? "" })}
        description={t("organize.deleteFolderDescription")}
        confirmLabel={t("common.delete")}
        onConfirm={() =>
          deleting &&
          remove.mutate(deleting.id, {
            onSuccess: () => toast.add({ title: t("organize.folderDeleted") }),
            onError,
          })
        }
      />
    </SettingsSection>
  )
}

function FolderDialog({
  folder,
  onClose,
}: {
  folder: Folder | null
  onClose: () => void
}) {
  const { t } = useTranslation()
  const conversations = useQuery(chatQueries.list(false))
  const labelOf = useConversationLabel()
  const save = useSaveFolder()
  const [name, setName] = useState(folder?.name ?? "")
  const [selected, setSelected] = useState<string[]>(
    folder?.conversationIds ?? []
  )
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent>
        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            save.mutate(
              { id: folder?.id, name, conversationIds: selected },
              {
                onSuccess: () => {
                  toast.add({ title: t("organize.folderSaved") })
                  onClose()
                },
                onError: (error) =>
                  toast.add({
                    title: errorMessage(error, t("common.genericError")),
                    type: "error",
                  }),
              }
            )
          }}
        >
          <DialogHeader>
            <DialogTitle>
              {folder
                ? t("organize.editFolder", { name: folder.name })
                : t("organize.newFolder")}
            </DialogTitle>
          </DialogHeader>
          <Field>
            <FieldLabel htmlFor="folder-name">
              {t("organize.folderName")}
            </FieldLabel>
            <Input
              id="folder-name"
              value={name}
              required
              maxLength={40}
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <fieldset className="flex max-h-64 min-w-0 flex-col gap-1 overflow-y-auto">
            <legend className="mb-1 text-sm font-medium">
              {t("organize.folderChats")}
            </legend>
            {(conversations.data ?? []).map((conversation) => (
              <label
                key={conversation.id}
                className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md px-2 py-1 hover:bg-muted"
              >
                <Checkbox
                  checked={selected.includes(conversation.id)}
                  onCheckedChange={(checked) =>
                    setSelected((current) =>
                      checked
                        ? [...current, conversation.id]
                        : current.filter((id) => id !== conversation.id)
                    )
                  }
                />
                <span data-private="names" className="truncate text-sm">
                  {labelOf(conversation)}
                </span>
              </label>
            ))}
          </fieldset>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={!name.trim() || save.isPending}>
              {t("organize.saveFolder")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** "Manter arquivadas ao receber nova mensagem" (GR-05). */
export function KeepArchivedSetting() {
  const { t } = useTranslation()
  const preferences = useQuery(chatQueries.preferences())
  const change = useChangePreferences()
  const onError = useErrorToast()
  return (
    <SettingsSection
      title={t("organize.keepArchivedTitle")}
      description={t("organize.keepArchivedDescription")}
    >
      <label className="flex min-h-11 items-center justify-between gap-3 text-sm">
        <span className="min-w-0">{t("organize.keepArchived")}</span>
        <Switch
          checked={preferences.data?.keepArchived ?? false}
          disabled={preferences.isPending || change.isPending}
          onCheckedChange={(keepArchived) =>
            change.mutate(
              { keepArchived },
              {
                onSuccess: () => toast.add({ title: t("common.saved") }),
                onError,
              }
            )
          }
        />
      </label>
    </SettingsSection>
  )
}
