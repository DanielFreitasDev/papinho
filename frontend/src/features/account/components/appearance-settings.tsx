import { flushSync } from "react-dom"
import { useTranslation } from "react-i18next"
import { MonitorSmartphoneIcon, MoonIcon, SunIcon } from "lucide-react"
import { Radio } from "@base-ui/react/radio"
import { RadioGroup } from "@base-ui/react/radio-group"

import { SwatchPicker } from "@/components/swatch-picker"
import { revealAppearance, useTheme } from "@/components/theme-provider"
import {
  Field,
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
import {
  DENSITIES,
  FONT_SIZES,
  PALETTES,
  setDisplayPrefs,
  useDisplayPrefs,
} from "@/lib/display-prefs"
import i18n, { SUPPORTED_LANGUAGES, type Language } from "@/lib/i18n"
import { SettingsSection } from "./settings-section"
import { WallpaperPicker } from "./wallpaper-picker"

const LANGUAGE_NAMES: Record<Language, string> = {
  "pt-BR": "Português (Brasil)",
  en: "English",
}

const THEMES = [
  { value: "light", icon: SunIcon },
  { value: "dark", icon: MoonIcon },
  { value: "system", icon: MonitorSmartphoneIcon },
] as const

/**
 * Tema claro, escuro ou do sistema, paleta de cor, idioma (IN-01), papel de parede, tamanho da fonte e densidade
 * (UI-02); paleta, papel de parede, fonte e densidade valem para este aparelho.
 */
export function AppearanceSettings() {
  const { t } = useTranslation()
  const { theme, setTheme } = useTheme()
  const display = useDisplayPrefs()
  const languages = SUPPORTED_LANGUAGES.map((value) => ({
    value,
    label: LANGUAGE_NAMES[value],
  }))

  return (
    <SettingsSection title={t("settings.appearance")}>
      <FieldGroup>
        <Field>
          <FieldLabel id="appearance-theme">{t("appearance.theme")}</FieldLabel>
          {/* Três cartões com ícone (como a escolha de tema do WhatsApp), um grupo de rádios. */}
          <RadioGroup
            aria-labelledby="appearance-theme"
            value={theme}
            onValueChange={(value) => setTheme(value as typeof theme)}
            className="grid grid-cols-3 gap-2 sm:max-w-md"
          >
            {THEMES.map(({ value, icon: Icon }) => (
              <label
                key={value}
                className="group/theme flex cursor-pointer flex-col items-center gap-1.5 rounded-xl border bg-card px-2 py-3 text-sm transition-[border-color,background-color,transform] duration-(--duration-fast) active:scale-[0.97] has-focus-visible:ring-3 has-focus-visible:ring-ring/50 has-data-checked:border-primary has-data-checked:bg-primary/10 has-data-checked:font-medium pointer-coarse:min-h-11 [@media(hover:hover)]:hover:bg-muted"
              >
                <Radio.Root value={value} className="sr-only" />
                <Icon
                  aria-hidden="true"
                  className="size-5 transition-transform duration-(--duration-base) ease-(--ease-spring) group-has-data-checked/theme:scale-110 group-has-data-checked/theme:text-primary"
                />
                {t(`appearance.${value}`)}
              </label>
            ))}
          </RadioGroup>
        </Field>
        <Field>
          <FieldLabel id="appearance-palette">
            {t("appearance.palette")}
          </FieldLabel>
          <FieldDescription>{t("appearance.paletteHint")}</FieldDescription>
          <SwatchPicker
            label={t("appearance.palette")}
            value={display.palette}
            sections={[
              {
                options: PALETTES.map((value) => ({
                  value,
                  label: t(`appearance.palettes.${value}`),
                  swatch: `palette-${value}`,
                })),
              },
            ]}
            onChange={(palette) =>
              // O app inteiro muda de cor num círculo que cresce a partir do toque, como na troca de tema.
              revealAppearance(() =>
                flushSync(() => setDisplayPrefs({ palette }))
              )
            }
          />
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
        <WallpaperPicker />
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
