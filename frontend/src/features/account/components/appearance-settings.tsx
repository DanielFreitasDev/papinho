import { useTranslation } from "react-i18next"

import { useTheme } from "@/components/theme-provider"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import i18n, { SUPPORTED_LANGUAGES, type Language } from "@/lib/i18n"
import { SettingsSection } from "./settings-section"

const LANGUAGE_NAMES: Record<Language, string> = {
  "pt-BR": "Português (Brasil)",
  en: "English",
}

/** Tema claro, escuro ou do sistema e idioma da interface (UI-02, IN-01). */
export function AppearanceSettings() {
  const { t } = useTranslation()
  const { theme, setTheme } = useTheme()
  const themes = [
    { value: "system", label: t("appearance.system") },
    { value: "light", label: t("appearance.light") },
    { value: "dark", label: t("appearance.dark") },
  ]
  const languages = SUPPORTED_LANGUAGES.map((value) => ({
    value,
    label: LANGUAGE_NAMES[value],
  }))

  return (
    <SettingsSection title={t("settings.appearance")}>
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="appearance-theme">
            {t("appearance.theme")}
          </FieldLabel>
          <Select
            items={themes}
            value={theme}
            onValueChange={(value) => setTheme(value as typeof theme)}
          >
            <SelectTrigger id="appearance-theme" className="w-full sm:w-64">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {themes.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        <Field>
          <FieldLabel htmlFor="appearance-language">
            {t("appearance.language")}
          </FieldLabel>
          <Select
            items={languages}
            value={i18n.language}
            onValueChange={(value) => {
              localStorage.setItem("papinho.language", String(value))
              void i18n.changeLanguage(String(value))
            }}
          >
            <SelectTrigger id="appearance-language" className="w-full sm:w-64">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {languages.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
      </FieldGroup>
    </SettingsSection>
  )
}
