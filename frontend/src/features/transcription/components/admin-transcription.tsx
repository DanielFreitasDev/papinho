import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { QueryState } from "@/components/query-state"
import { PasswordInput } from "@/components/password-input"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
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
import { errorMessage } from "@/lib/api-client"
import {
  transcriptionQueries,
  useAdminTranscription,
  type AdminProvider,
  type ProviderKind,
} from "../api"
import { useErrorToast } from "@/hooks/use-error-toast"

const KINDS: ProviderKind[] = ["GROQ", "WHISPER", "OPENAI", "CUSTOM"]

/** Provedores de transcrição e o padrão da instância (AU-05, AD-01). */
export function AdminTranscription() {
  const { t } = useTranslation()
  const view = useQuery(transcriptionQueries.admin())
  const actions = useAdminTranscription()
  const onError = useErrorToast()
  const [kind, setKind] = useState<ProviderKind>("GROQ")
  if (!view.data) {
    return <QueryState query={view}>{() => null}</QueryState>
  }
  const defaults = [
    { value: "BROWSER", label: t("transcription.browser") },
    ...view.data.providers.map((p) => ({ value: p.id, label: p.name })),
  ]
  const kinds = KINDS.map((value) => ({
    value,
    label: t(`transcription.kind.${value}`),
  }))
  return (
    <div className="flex flex-col gap-4">
      <Field>
        <FieldLabel htmlFor="transcription-default">
          {t("transcription.adminDefault")}
        </FieldLabel>
        <Select
          items={defaults}
          value={view.data.defaultProviderId ?? "BROWSER"}
          onValueChange={(value) =>
            actions.setDefault.mutate(value === "BROWSER" ? null : value, {
              onError,
            })
          }
        >
          <SelectTrigger id="transcription-default" className="w-full sm:w-72">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {defaults.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </Field>
      <ul className="flex flex-col gap-3">
        {view.data.providers.map((provider) => (
          <ProviderForm key={provider.id} provider={provider} />
        ))}
      </ul>
      <div className="flex flex-wrap items-end gap-2">
        <Field className="w-auto">
          <FieldLabel htmlFor="transcription-new-kind">
            {t("transcription.newProvider")}
          </FieldLabel>
          <Select
            items={kinds}
            value={kind}
            onValueChange={(value) => value && setKind(value as ProviderKind)}
          >
            <SelectTrigger id="transcription-new-kind" className="w-56">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {kinds.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        <Button onClick={() => actions.create.mutate(kind, { onError })}>
          {t("transcription.add")}
        </Button>
      </div>
    </div>
  )
}

function ProviderForm({ provider }: { provider: AdminProvider }) {
  const { t } = useTranslation()
  const actions = useAdminTranscription()
  const onError = useErrorToast()
  const [values, setValues] = useState({
    name: provider.name,
    baseUrl: provider.baseUrl,
    model: provider.model,
    hotwords: provider.hotwords ?? "",
    maxMegabytes: Math.round(provider.maxBytes / (1024 * 1024)),
    enabled: provider.enabled,
  })
  const [apiKey, setApiKey] = useState("")
  const id = (field: string) => `provider-${provider.id}-${field}`
  return (
    <li className="rounded-xl border p-3">
      <form
        noValidate
        className="flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          actions.update.mutate(
            {
              id: provider.id,
              name: values.name,
              baseUrl: values.baseUrl,
              model: values.model,
              hotwords: values.hotwords || null,
              maxBytes: values.maxMegabytes * 1024 * 1024,
              enabled: values.enabled,
              apiKey: apiKey === "" ? null : apiKey,
            },
            {
              onSuccess: () => {
                setApiKey("")
                toast.add({ title: t("common.saved") })
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
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="font-medium">{provider.name}</h2>
          <Badge variant="secondary">
            {t(`transcription.kind.${provider.kind}`)}
          </Badge>
          {provider.hasKey ? (
            <Badge variant="outline">{t("transcription.hasKey")}</Badge>
          ) : null}
        </div>
        <FieldGroup className="gap-3">
          <Field>
            <FieldLabel htmlFor={id("name")}>
              {t("transcription.name")}
            </FieldLabel>
            <Input
              id={id("name")}
              value={values.name}
              maxLength={80}
              onChange={(e) => setValues({ ...values, name: e.target.value })}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor={id("url")}>
              {t("transcription.baseUrl")}
            </FieldLabel>
            <Input
              id={id("url")}
              type="url"
              value={values.baseUrl}
              onChange={(e) =>
                setValues({ ...values, baseUrl: e.target.value })
              }
            />
          </Field>
          <Field>
            <FieldLabel htmlFor={id("model")}>
              {t("transcription.model")}
            </FieldLabel>
            <Input
              id={id("model")}
              value={values.model}
              onChange={(e) => setValues({ ...values, model: e.target.value })}
            />
          </Field>
          {provider.kind === "WHISPER" ? (
            <Field>
              <FieldLabel htmlFor={id("hotwords")}>
                {t("transcription.hotwords")}
              </FieldLabel>
              <Input
                id={id("hotwords")}
                value={values.hotwords}
                onChange={(e) =>
                  setValues({ ...values, hotwords: e.target.value })
                }
              />
            </Field>
          ) : null}
          <Field>
            <FieldLabel htmlFor={id("key")}>
              {t("transcription.apiKey")}
            </FieldLabel>
            <PasswordInput
              id={id("key")}
              autoComplete="off"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
            />
            <FieldDescription>{t("transcription.apiKeyHelp")}</FieldDescription>
          </Field>
          <Field>
            <FieldLabel htmlFor={id("max")}>
              {t("transcription.maxMegabytes")}
            </FieldLabel>
            <Input
              id={id("max")}
              type="number"
              min={1}
              max={1024}
              value={values.maxMegabytes}
              onChange={(e) =>
                setValues({ ...values, maxMegabytes: Number(e.target.value) })
              }
            />
          </Field>
          <Field orientation="horizontal">
            <Switch
              id={id("enabled")}
              checked={values.enabled}
              onCheckedChange={(checked) =>
                setValues({ ...values, enabled: checked })
              }
            />
            <FieldLabel htmlFor={id("enabled")}>
              {t("transcription.enabled")}
            </FieldLabel>
          </Field>
        </FieldGroup>
        <div className="flex flex-wrap justify-end gap-2">
          {provider.kind === "WHISPER" ? (
            <Button
              type="button"
              variant="outline"
              onClick={() =>
                actions.health.mutate(provider.id, {
                  onSuccess: (result) =>
                    toast.add({
                      title: result.healthy
                        ? t("transcription.healthy")
                        : t("transcription.unhealthy"),
                      type: result.healthy ? "success" : "error",
                    }),
                  onError,
                })
              }
            >
              {t("transcription.checkHealth")}
            </Button>
          ) : null}
          <Button
            type="button"
            variant="ghost"
            onClick={() => actions.remove.mutate(provider.id, { onError })}
          >
            {t("common.delete")}
          </Button>
          <Button type="submit">{t("common.save")}</Button>
        </div>
      </form>
    </li>
  )
}
