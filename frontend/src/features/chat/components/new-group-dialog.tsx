import { useState } from "react"
import { useNavigate } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"
import { useCreateGroup } from "../groups"
import { ContactPicker } from "./contact-picker"

/** Novo grupo: nome e contatos (GR-01). */
export function NewGroupDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const create = useCreateGroup()
  const [name, setName] = useState("")
  const [members, setMembers] = useState<string[]>([])

  function close() {
    setName("")
    setMembers([])
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? null : close())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("groups.newGroup")}</DialogTitle>
          <DialogDescription>
            {t("groups.newGroupDescription")}
          </DialogDescription>
        </DialogHeader>
        <form
          id="new-group"
          className="flex min-h-0 flex-col gap-4"
          onSubmit={async (event) => {
            event.preventDefault()
            try {
              const group = await create.mutateAsync({
                name: name.trim(),
                memberIds: members,
              })
              close()
              await navigate({
                to: "/c/$conversationId",
                params: { conversationId: group.id },
              })
            } catch (error) {
              toast.add({
                title: errorMessage(error, t("common.genericError")),
                type: "error",
              })
            }
          }}
        >
          <Field>
            <FieldLabel htmlFor="group-name">{t("groups.name")}</FieldLabel>
            <Input
              id="group-name"
              required
              maxLength={100}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <ContactPicker selected={members} onChange={setMembers} />
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={close}>
            {t("common.cancel")}
          </Button>
          <Button
            type="submit"
            form="new-group"
            disabled={name.trim() === "" || create.isPending}
          >
            {create.isPending ? <Spinner data-icon="inline-start" /> : null}
            {t("groups.create")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
