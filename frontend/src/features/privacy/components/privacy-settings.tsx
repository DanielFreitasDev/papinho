import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

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
  Field,
  FieldContent,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import { SettingsSection } from "@/features/account"
import { contactQueries } from "@/features/contact"
import { errorMessage } from "@/lib/api-client"
import { privacyQueries, useReplaceExceptions, useUpdatePrivacy } from "../api"
import { BLUR_ITEMS, REVEAL_DELAYS } from "../private-mode"
import type {
  PrivacyItem,
  PrivacySettings as Settings,
  PrivacySettingsChange,
  Visibility,
} from "../types"
import { ScreenLockSettings } from "./screen-lock-settings"

const VISIBILITIES: Visibility[] = [
  "EVERYONE",
  "CONTACTS",
  "CONTACTS_EXCEPT",
  "NOBODY",
]

const ITEMS: {
  item: PrivacyItem
  field: "lastSeen" | "online" | "photo" | "about" | "groupsAdd"
}[] = [
  { item: "LAST_SEEN", field: "lastSeen" },
  { item: "ONLINE", field: "online" },
  { item: "PHOTO", field: "photo" },
  { item: "ABOUT", field: "about" },
]

const BLUR_FIELDS = {
  messages: "blurMessages",
  previews: "blurPreviews",
  names: "blurNames",
  photos: "blurPhotos",
  media: "blurMedia",
} as const

/** Configurações > Privacidade (PV-01, PV-04..PV-08) e o bloqueio de tela (PV-02). */
export function PrivacySettings() {
  const { t } = useTranslation()
  const privacy = useQuery(privacyQueries.settings())
  const update = useUpdatePrivacy()

  function change(next: PrivacySettingsChange) {
    update.mutate(next, {
      onSuccess: () => toast.add({ title: t("common.saved") }),
      onError: (error) =>
        toast.add({
          title: errorMessage(error, t("common.genericError")),
          type: "error",
        }),
    })
  }

  if (privacy.isPending) {
    return <Skeleton className="h-64 w-full" />
  }
  if (privacy.isError) {
    return <p role="alert">{t("errors.genericDescription")}</p>
  }
  const { settings, exceptions } = privacy.data
  return (
    <div className="flex flex-col gap-4">
      <SettingsSection
        title={t("privacy.whoCanSee")}
        description={t("privacy.whoCanSeeDescription")}
      >
        <FieldGroup>
          {ITEMS.map(({ item, field }) => (
            <VisibilityField
              key={item}
              item={item}
              value={settings[field]}
              excluded={exceptions[item]}
              disabled={update.isPending}
              onChange={(value) => change({ [field]: value })}
            />
          ))}
        </FieldGroup>
      </SettingsSection>

      <SettingsSection
        title={t("privacy.groupsTitle")}
        description={t("privacy.groupsDescription")}
      >
        <VisibilityField
          item="GROUPS_ADD"
          value={settings.groupsAdd}
          excluded={exceptions.GROUPS_ADD}
          disabled={update.isPending}
          onChange={(value) => change({ groupsAdd: value })}
        />
      </SettingsSection>

      <SettingsSection title={t("privacy.messagesTitle")}>
        <FieldGroup>
          <SwitchField
            id="privacy-read-receipts"
            label={t("privacy.readReceipts")}
            description={t("privacy.readReceiptsDescription")}
            checked={settings.readReceipts}
            disabled={update.isPending}
            onChange={(readReceipts) => change({ readReceipts })}
          />
          <SwitchField
            id="privacy-hide-content"
            label={t("privacy.hideNotificationContent")}
            description={t("privacy.hideNotificationContentDescription")}
            checked={settings.hideNotificationContent}
            disabled={update.isPending}
            onChange={(hideNotificationContent) =>
              change({ hideNotificationContent })
            }
          />
        </FieldGroup>
      </SettingsSection>

      <BlurSection settings={settings} onChange={change} />
      <ScreenLockSettings />
    </div>
  )
}

function BlurSection({
  settings,
  onChange,
}: {
  settings: Settings
  onChange: (change: PrivacySettingsChange) => void
}) {
  const { t } = useTranslation()
  const blur = settings.blur
  const delays = REVEAL_DELAYS.map((delay) => ({
    value: String(delay),
    label: t(`privacy.blur.delay.${delay}`),
  }))
  return (
    <SettingsSection
      title={t("privacy.blur.title")}
      description={t("privacy.blur.description")}
    >
      <FieldGroup>
        <SwitchField
          id="privacy-private-mode"
          label={t("privacy.blur.privateMode")}
          description={t("privacy.blur.shortcut")}
          checked={blur.privateMode}
          onChange={(privateMode) => onChange({ privateMode })}
        />
        <fieldset className="flex min-w-0 flex-col gap-3">
          <legend className="mb-2 text-sm font-medium">
            {t("privacy.blur.items")}
          </legend>
          {BLUR_ITEMS.map((item) => (
            <Field key={item} orientation="horizontal">
              <Checkbox
                id={`privacy-blur-${item}`}
                checked={blur[item]}
                onCheckedChange={(checked) =>
                  onChange({ [BLUR_FIELDS[item]]: checked })
                }
              />
              <FieldLabel htmlFor={`privacy-blur-${item}`}>
                {t(`privacy.blur.item.${item}`)}
              </FieldLabel>
            </Field>
          ))}
        </fieldset>
        <Field>
          <FieldLabel htmlFor="privacy-reveal-delay">
            {t("privacy.blur.reveal")}
          </FieldLabel>
          <Select
            items={delays}
            value={String(blur.revealDelayMs)}
            onValueChange={(value) =>
              onChange({ revealDelayMs: Number(value ?? 0) })
            }
          >
            <SelectTrigger id="privacy-reveal-delay" className="w-full sm:w-64">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {delays.map((delay) => (
                  <SelectItem key={delay.value} value={delay.value}>
                    {delay.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          <FieldDescription>{t("privacy.blur.revealHint")}</FieldDescription>
        </Field>
      </FieldGroup>
    </SettingsSection>
  )
}

function SwitchField({
  id,
  label,
  description,
  checked,
  disabled,
  onChange,
}: {
  id: string
  label: string
  description?: string
  checked: boolean
  disabled?: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <Field orientation="horizontal">
      <FieldContent>
        <FieldLabel htmlFor={id}>{label}</FieldLabel>
        {description ? (
          <FieldDescription>{description}</FieldDescription>
        ) : null}
      </FieldContent>
      <Switch
        id={id}
        checked={checked}
        disabled={disabled}
        onCheckedChange={onChange}
      />
    </Field>
  )
}

/** Escolha de quem vê (ou quem adiciona), com a lista "exceto…" quando é o caso. */
function VisibilityField({
  item,
  value,
  excluded,
  disabled,
  onChange,
}: {
  item: PrivacyItem
  value: Visibility
  excluded: string[]
  disabled?: boolean
  onChange: (value: Visibility) => void
}) {
  const { t } = useTranslation()
  const [picking, setPicking] = useState(false)
  const id = `privacy-${item.toLowerCase()}`
  const options = VISIBILITIES.map((visibility) => ({
    value: visibility,
    label: t(`privacy.visibility.${visibility}`),
  }))
  return (
    <Field>
      <FieldLabel htmlFor={id}>{t(`privacy.item.${item}`)}</FieldLabel>
      <div className="flex flex-wrap items-center gap-2">
        <Select
          items={options}
          value={value}
          disabled={disabled}
          onValueChange={(next) => {
            if (next) {
              onChange(next as Visibility)
              if (next === "CONTACTS_EXCEPT") {
                setPicking(true)
              }
            }
          }}
        >
          <SelectTrigger id={id} className="w-full sm:w-64">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {options.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
        {value === "CONTACTS_EXCEPT" ? (
          <Button variant="outline" onClick={() => setPicking(true)}>
            {t("privacy.exceptions.choose", { count: excluded.length })}
          </Button>
        ) : null}
      </div>
      {item === "LAST_SEEN" || item === "ONLINE" ? (
        <FieldDescription>{t("privacy.reciprocity")}</FieldDescription>
      ) : null}
      {picking ? (
        <ExceptionsDialog
          item={item}
          excluded={excluded}
          onClose={() => setPicking(false)}
        />
      ) : null}
    </Field>
  )
}

function ExceptionsDialog({
  item,
  excluded,
  onClose,
}: {
  item: PrivacyItem
  excluded: string[]
  onClose: () => void
}) {
  const { t } = useTranslation()
  const contacts = useQuery(contactQueries.list())
  const replace = useReplaceExceptions()
  const [selected, setSelected] = useState(() => new Set(excluded))
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(`privacy.item.${item}`)}</DialogTitle>
          <DialogDescription>
            {t("privacy.exceptions.description")}
          </DialogDescription>
        </DialogHeader>
        <div className="flex max-h-72 flex-col gap-3 overflow-y-auto">
          {contacts.isPending ? <Skeleton className="h-9 w-full" /> : null}
          {contacts.data?.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              {t("privacy.exceptions.noContacts")}
            </p>
          ) : null}
          {contacts.data?.map((contact) => (
            <Field key={contact.userId} orientation="horizontal">
              <Checkbox
                id={`exception-${item}-${contact.userId}`}
                checked={selected.has(contact.userId)}
                onCheckedChange={(checked) =>
                  setSelected((current) => {
                    const next = new Set(current)
                    if (checked) {
                      next.add(contact.userId)
                    } else {
                      next.delete(contact.userId)
                    }
                    return next
                  })
                }
              />
              <FieldLabel htmlFor={`exception-${item}-${contact.userId}`}>
                {contact.label}
              </FieldLabel>
            </Field>
          ))}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button
            disabled={replace.isPending}
            onClick={() =>
              replace.mutate(
                { item, userIds: [...selected] },
                {
                  onSuccess: () => {
                    toast.add({ title: t("common.saved") })
                    onClose()
                  },
                  onError: (error) =>
                    toast.add({
                      title: errorMessage(error, t("common.genericError")),
                      type: "error",
                    }),
                }
              )
            }
          >
            {t("common.save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
