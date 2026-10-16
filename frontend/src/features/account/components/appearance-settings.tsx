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
import {
  DENSITIES,
  FONT_SIZES,
  WALLPAPERS,
  setDisplayPrefs,
  useDisplayPrefs,
} from "@/lib/display-prefs"
import i18n, { SUPPORTED_LANGUAGES, type Language } from "@/lib/i18n"
import { SettingsSection } from "./settings-section"

const LANGUAGE_NAMES: Record<Language, string> = {
  "pt-BR": "Português (Brasil)",
  en: "English",
}

/**
 * Tema claro, escuro ou do sistema, idioma (IN-01), papel de parede, tamanho da fonte e densidade (UI-02); as três
 * últimas valem para este aparelho.
 */
export function AppearanceSettings() {
  const { t } = useTranslation()
  const { theme, setTheme } = useTheme()
  const display = useDisplayPrefs()
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
        <ChoiceField
          id="appearance-wallpaper"
          label={t("appearance.wallpaper")}
          value={display.wallpaper}
          options={WALLPAPERS.map((value) => ({
            value,
            label: t(`appearance.wallpapers.${value}`),
          }))}
          onChange={(wallpaper) => setDisplayPrefs({ wallpaper })}
        />
        <ChoiceField
          id="appearance-font-size"
          label={t("appearance.fontSize")}
          value={display.fontSize}
          options={FONT_SIZES.map((value) => ({
            value,
            label: t(`appearance.fontSizes.${value}`),
          }))}
          onChange={(fontSize) => setDisplayPrefs({ fontSize })}
        />
        <ChoiceField
          id="appearance-density"
          label={t("appearance.density")}
          value={display.density}
          options={DENSITIES.map((value) => ({
            value,
            label: t(`appearance.densities.${value}`),
          }))}
          onChange={(density) => setDisplayPrefs({ density })}
        />
      </FieldGroup>
    </SettingsSection>
  )
}

/** Uma escolha entre opções fixas, no mesmo formato das outras da tela. */
function ChoiceField<T extends string>({
  id,
  label,
  value,
  options,
  onChange,
}: {
  id: string
  label: string
  value: T
  options: { value: T; label: string }[]
  onChange: (value: T) => void
}) {
  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Select
        items={options}
        value={value}
        onValueChange={(next) => onChange(next as T)}
      >
        <SelectTrigger id={id} className="w-full sm:w-64">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {options.map((item) => (
              <SelectItem key={item.value} value={item.value}>
                {item.label}
              </SelectItem>
            ))}
          </SelectGroup>
        </SelectContent>
      </Select>
    </Field>
  )
}
