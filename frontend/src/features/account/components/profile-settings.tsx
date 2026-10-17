import { useCallback, useRef, useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { z } from "@/lib/zod"
import { CameraIcon, TrashIcon } from "lucide-react"

import { FormFieldError } from "@/components/form-error"
import { UserAvatar } from "@/components/user-avatar"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
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
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { toast } from "@/components/ui/toast"
import { authQueries, displayNameSchema } from "@/features/auth"
import { errorMessage } from "@/lib/api-client"
import { useChangePhoto, useRemovePhoto, useUpdateProfile } from "../api"
import { PhotoCropper } from "./photo-cropper"
import { SettingsSection } from "./settings-section"
import { useErrorToast } from "@/hooks/use-error-toast"

const schema = z.object({
  displayName: displayNameSchema,
  about: z.string().trim().max(140, "validation.aboutMax"),
})

type Values = z.input<typeof schema>

/** Nome de exibição, recado e foto (ID-02). */
export function ProfileSettings() {
  const { t } = useTranslation()
  const me = useQuery(authQueries.me())
  const update = useUpdateProfile()
  const changePhoto = useChangePhoto()
  const removePhoto = useRemovePhoto()
  const onError = useErrorToast()
  const fileInput = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [cropped, setCropped] = useState<Blob | null>(null)
  const [error, setError] = useState<string | null>(null)
  const profile = me.data?.profile
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: {
      displayName: profile?.displayName ?? "",
      about: profile?.about ?? "",
    },
  })
  const onCrop = useCallback((blob: Blob | null) => setCropped(blob), [])

  if (!profile) {
    return null
  }

  const submit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      const parsed = schema.parse(values)
      await update.mutateAsync({
        displayName: parsed.displayName,
        about: parsed.about || null,
      })
      toast.add({ title: t("common.saved") })
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
    }
  })

  async function savePhoto() {
    if (!cropped) {
      return
    }
    try {
      await changePhoto.mutateAsync(cropped)
      toast.add({ title: t("profile.photoSaved") })
      setFile(null)
    } catch (caught) {
      setError(errorMessage(caught, t("common.genericError")))
      setFile(null)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <SettingsSection title={t("profile.photo")}>
        <div className="flex flex-wrap items-center gap-4">
          <UserAvatar
            userId={profile.userId}
            name={profile.displayName}
            hasPhoto={profile.hasPhoto}
            version={profile.updatedAt}
            className="size-20"
          />
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              onClick={() => fileInput.current?.click()}
            >
              <CameraIcon data-icon="inline-start" />
              {t("profile.changePhoto")}
            </Button>
            {profile.hasPhoto ? (
              <Button
                variant="ghost"
                onClick={() => removePhoto.mutate(undefined, { onError })}
                disabled={removePhoto.isPending}
              >
                <TrashIcon data-icon="inline-start" />
                {t("profile.removePhoto")}
              </Button>
            ) : null}
          </div>
          <input
            ref={fileInput}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
            className="sr-only"
            tabIndex={-1}
            aria-hidden="true"
            onChange={(event) => {
              setFile(event.target.files?.[0] ?? null)
              event.target.value = ""
            }}
          />
        </div>
      </SettingsSection>
      <SettingsSection title={t("settings.profile")}>
        <form onSubmit={submit} noValidate>
          <FieldGroup>
            {error ? (
              <Alert variant="destructive" role="alert">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            <Field data-invalid={!!form.formState.errors.displayName}>
              <FieldLabel htmlFor="profile-name">
                {t("auth.displayName")}
              </FieldLabel>
              <Input
                id="profile-name"
                autoComplete="name"
                aria-invalid={!!form.formState.errors.displayName}
                {...form.register("displayName")}
              />
              <FormFieldError error={form.formState.errors.displayName} />
            </Field>
            <Field data-invalid={!!form.formState.errors.about}>
              <FieldLabel htmlFor="profile-about">
                {t("profile.about")}
              </FieldLabel>
              <Textarea
                id="profile-about"
                rows={2}
                maxLength={140}
                {...form.register("about")}
              />
              <FieldDescription>{t("profile.aboutHint")}</FieldDescription>
              <FormFieldError error={form.formState.errors.about} />
            </Field>
            <Field orientation="horizontal">
              <Button type="submit" disabled={update.isPending}>
                {update.isPending ? <Spinner data-icon="inline-start" /> : null}
                {t("common.save")}
              </Button>
            </Field>
          </FieldGroup>
        </form>
      </SettingsSection>
      <Dialog
        open={file !== null}
        onOpenChange={(open) => !open && setFile(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("profile.cropTitle")}</DialogTitle>
            <DialogDescription>
              {t("profile.cropDescription")}
            </DialogDescription>
          </DialogHeader>
          {file ? <PhotoCropper file={file} onChange={onCrop} /> : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setFile(null)}>
              {t("common.cancel")}
            </Button>
            <Button
              onClick={savePhoto}
              disabled={!cropped || changePhoto.isPending}
            >
              {changePhoto.isPending ? (
                <Spinner data-icon="inline-start" />
              ) : null}
              {t("common.save")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
