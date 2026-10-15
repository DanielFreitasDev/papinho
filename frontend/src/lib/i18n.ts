import i18n from "i18next"
import { initReactI18next } from "react-i18next"

import en from "@/locales/en.json"
import ptBR from "@/locales/pt-BR.json"

/** Idiomas suportados; pt-BR é o padrão e a referência de chaves (IN-01). */
export const SUPPORTED_LANGUAGES = ["pt-BR", "en"] as const
export type Language = (typeof SUPPORTED_LANGUAGES)[number]

export const resources = {
  "pt-BR": { translation: ptBR },
  en: { translation: en },
} as const

/** pt-BR é o idioma do produto; outro idioma só por escolha explícita do usuário. */
function detectLanguage(): Language {
  const stored = localStorage.getItem("papinho.language")
  if (stored && (SUPPORTED_LANGUAGES as readonly string[]).includes(stored)) {
    return stored as Language
  }
  return "pt-BR"
}

void i18n.use(initReactI18next).init({
  resources,
  lng: detectLanguage(),
  fallbackLng: "pt-BR",
  interpolation: { escapeValue: false },
  returnNull: false,
})

i18n.on("languageChanged", (lng) => {
  document.documentElement.lang = lng
})

export default i18n
