import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { QueryState } from "@/components/query-state"
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import { SettingsSection } from "@/features/account"
import { transcriptionQueries, useSavePreference } from "../api"
import { setWhisperModel, whisperModel } from "../browser-whisper"
import type { WhisperModel } from "../protocol"
import { useErrorToast } from "@/hooks/use-error-toast"

/** Preferências de transcrição do usuário (AU-03, AU-04, AU-05). */
export function TranscriptionSettings() {
  const { t } = useTranslation()
  const options = useQuery(transcriptionQueries.options())
  const save = useSavePreference()
  const onError = useErrorToast()
  const [model, setModel] = useState<WhisperModel>(whisperModel())
  if (!options.data) {
    return (
      <SettingsSection title={t("settings.transcription")}>
        <QueryState query={options}>{() => null}</QueryState>
      </SettingsSection>
    )
  }
  const { preference, providers, instanceDefault } = options.data
  const defaultName =
    instanceDefault === "BROWSER"
      ? t("transcription.browser")
      : (providers.find((p) => p.id === instanceDefault)?.name ??
        t("transcription.browser"))
  const items = [
    {
      value: "DEFAULT",
      label: t("transcription.instanceDefault", { name: defaultName }),
    },
    { value: "BROWSER", label: t("transcription.browser") },
    ...providers.map((p) => ({ value: p.id, label: p.name })),
  ]
  const update = (next: { provider?: string; auto?: boolean }) =>
    save.mutate(
      { ...preference, ...next },
      { onSuccess: () => toast.add({ title: t("common.saved") }), onError }
    )
  return (
    <SettingsSection title={t("settings.transcription")}>
      <div className="flex flex-col gap-4">
        <Field>
          <FieldLabel htmlFor="transcription-provider">
            {t("transcription.provider")}
          </FieldLabel>
          <Select
            items={items}
            value={preference.provider}
            onValueChange={(value) => value && update({ provider: value })}
          >
            <SelectTrigger
              id="transcription-provider"
              className="w-full sm:w-72"
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {items.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          <FieldDescription>{t("transcription.providerHelp")}</FieldDescription>
        </Field>
        <Field orientation="horizontal">
          <Switch
            id="transcription-auto"
            checked={preference.auto}
            onCheckedChange={(checked) => update({ auto: checked })}
          />
          <FieldLabel htmlFor="transcription-auto">
            {t("transcription.auto")}
          </FieldLabel>
        </Field>
        <Field>
          <FieldLabel htmlFor="transcription-model">
            {t("transcription.browserModel")}
          </FieldLabel>
          <Select
            items={[
              { value: "base", label: t("transcription.modelBase") },
              { value: "small", label: t("transcription.modelSmall") },
            ]}
            value={model}
            onValueChange={(value) => {
              if (value === "base" || value === "small") {
                setModel(value)
                setWhisperModel(value)
              }
            }}
          >
            <SelectTrigger id="transcription-model" className="w-full sm:w-72">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectItem value="base">
                  {t("transcription.modelBase")}
                </SelectItem>
                <SelectItem value="small">
                  {t("transcription.modelSmall")}
                </SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
          <FieldDescription>{t("transcription.browserHelp")}</FieldDescription>
        </Field>
      </div>
    </SettingsSection>
  )
}
