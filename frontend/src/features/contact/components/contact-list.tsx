import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  BanIcon,
  EllipsisVerticalIcon,
  PencilIcon,
  TrashIcon,
  UsersIcon,
} from "lucide-react"

import { ConfirmDialog } from "@/components/confirm-dialog"
import { UserAvatar } from "@/components/user-avatar"
import { Alert, AlertDescription } from "@/components/ui/alert"
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
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemMedia,
  ItemTitle,
} from "@/components/ui/item"
import { Skeleton } from "@/components/ui/skeleton"
import { toast } from "@/components/ui/toast"
import {
  contactQueries,
  useBlock,
  useRemoveContact,
  useRenameContact,
} from "../api"
import type { Contact } from "../types"

/**
 * Lista de contatos com estados de carregando, vazio e erro (UI-03) e ações por contato. Com {@code onOpen}, tocar
 * no contato abre a conversa com ele.
 */
export function ContactList({
  onOpen,
}: {
  onOpen?: (contact: Contact) => void
}) {
  const { t } = useTranslation()
  const contacts = useQuery(contactQueries.list())
  const [renaming, setRenaming] = useState<Contact | null>(null)
  const [blocking, setBlocking] = useState<Contact | null>(null)
  const [removing, setRemoving] = useState<Contact | null>(null)
  const rename = useRenameContact()
  const remove = useRemoveContact()
  const block = useBlock()

  if (contacts.isPending) {
    return (
      <div
        role="status"
        className="flex flex-col gap-3 p-3"
        aria-busy="true"
        aria-label={t("common.loading")}
      >
        {[0, 1, 2].map((index) => (
          <div key={index} className="flex items-center gap-3">
            <Skeleton className="size-10 rounded-full" />
            <Skeleton className="h-4 flex-1" />
          </div>
        ))}
      </div>
    )
  }
  if (contacts.isError) {
    return (
      <Alert variant="destructive" className="m-3">
        <AlertDescription>{t("errors.genericDescription")}</AlertDescription>
      </Alert>
    )
  }
  if (contacts.data.length === 0) {
    return (
      <Empty className="py-10">
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <UsersIcon />
          </EmptyMedia>
          <EmptyTitle>{t("contacts.empty")}</EmptyTitle>
          <EmptyDescription>{t("contacts.emptyDescription")}</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  return (
    <>
      <ItemGroup aria-label={t("shell.contacts")}>
        {contacts.data.map((contact) => (
          <Item
            key={contact.userId}
            size="sm"
            className={onOpen ? "relative hover:bg-muted" : undefined}
          >
            <ItemMedia>
              <UserAvatar
                privateItem
                userId={contact.userId}
                name={contact.label}
                hasPhoto={contact.hasPhoto}
                version={contact.photoVersion}
              />
            </ItemMedia>
            <ItemContent className="min-w-0">
              {onOpen ? (
                <button
                  type="button"
                  onClick={() => onOpen(contact)}
                  aria-label={t("chat.startConversation", {
                    name: contact.label,
                  })}
                  className="flex min-w-0 flex-col rounded-md text-left outline-none after:absolute after:inset-0 focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  <ItemTitle className="truncate" data-private="names">
                    {contact.label}
                  </ItemTitle>
                  <ItemDescription className="truncate">
                    @{contact.username}
                    {contact.about ? ` · ${contact.about}` : ""}
                  </ItemDescription>
                </button>
              ) : (
                <>
                  <ItemTitle className="truncate" data-private="names">
                    {contact.label}
                  </ItemTitle>
                  <ItemDescription className="truncate">
                    @{contact.username}
                    {contact.about ? ` · ${contact.about}` : ""}
                  </ItemDescription>
                </>
              )}
            </ItemContent>
            <ItemActions className="relative z-10">
              <DropdownMenu>
                <DropdownMenuTrigger
                  render={
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`${t("contacts.actions")}: ${contact.label}`}
                    />
                  }
                >
                  <EllipsisVerticalIcon />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuGroup>
                    <DropdownMenuItem onClick={() => setRenaming(contact)}>
                      <PencilIcon />
                      {t("contacts.rename")}
                    </DropdownMenuItem>
                    <DropdownMenuItem onClick={() => setBlocking(contact)}>
                      <BanIcon />
                      {t("contacts.block")}
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      variant="destructive"
                      onClick={() => setRemoving(contact)}
                    >
                      <TrashIcon />
                      {t("contacts.remove")}
                    </DropdownMenuItem>
                  </DropdownMenuGroup>
                </DropdownMenuContent>
              </DropdownMenu>
            </ItemActions>
          </Item>
        ))}
      </ItemGroup>
      <RenameDialog
        contact={renaming}
        onClose={() => setRenaming(null)}
        onSave={async (nickname) => {
          if (renaming) {
            await rename.mutateAsync({ userId: renaming.userId, nickname })
            toast.add({ title: t("common.saved") })
          }
          setRenaming(null)
        }}
      />
      <ConfirmDialog
        open={blocking !== null}
        onOpenChange={(open) => !open && setBlocking(null)}
        title={t("contacts.blockConfirmTitle", { name: blocking?.label ?? "" })}
        description={t("contacts.blockConfirmDescription")}
        confirmLabel={t("contacts.block")}
        onConfirm={() =>
          blocking && block.mutate({ userId: blocking.userId, blocked: true })
        }
      />
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => !open && setRemoving(null)}
        title={`${t("contacts.remove")}: ${removing?.label ?? ""}`}
        confirmLabel={t("contacts.remove")}
        onConfirm={() => removing && remove.mutate(removing.userId)}
      />
    </>
  )
}

function RenameDialog({
  contact,
  onClose,
  onSave,
}: {
  contact: Contact | null
  onClose: () => void
  onSave: (nickname: string | null) => Promise<void>
}) {
  const { t } = useTranslation()
  const [value, setValue] = useState("")
  return (
    <Dialog
      open={contact !== null}
      onOpenChange={(open) =>
        open ? setValue(contact?.nickname ?? "") : onClose()
      }
    >
      <DialogContent>
        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            void onSave(value.trim() === "" ? null : value.trim())
          }}
        >
          <DialogHeader>
            <DialogTitle>{t("contacts.rename")}</DialogTitle>
          </DialogHeader>
          <Field>
            <FieldLabel htmlFor="rename-nickname">
              {t("contacts.nickname")}
            </FieldLabel>
            <Input
              id="rename-nickname"
              maxLength={64}
              defaultValue={contact?.nickname ?? ""}
              onChange={(event) => setValue(event.target.value)}
              autoFocus
            />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t("common.cancel")}
            </Button>
            <Button type="submit">{t("common.save")}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
