// custom: troca de tema sem <style> inline (CSP estrita) e sem o atalho global "d" do template,
// que disparava ao digitar fora de campos; o tema é escolhido nas configurações (UI-02).
import * as React from "react"
import { flushSync } from "react-dom"

import { prefersReducedMotion } from "@/lib/motion"

type Theme = "dark" | "light" | "system"
type ResolvedTheme = "dark" | "light"

type ThemeProviderProps = {
  children: React.ReactNode
  defaultTheme?: Theme
  storageKey?: string
  disableTransitionOnChange?: boolean
}

type ThemeProviderState = {
  theme: Theme
  setTheme: (theme: Theme) => void
}

const COLOR_SCHEME_QUERY = "(prefers-color-scheme: dark)"
const THEME_VALUES: Theme[] = ["dark", "light", "system"]

const ThemeProviderContext = React.createContext<
  ThemeProviderState | undefined
>(undefined)

function isTheme(value: string | null): value is Theme {
  if (value === null) {
    return false
  }

  return THEME_VALUES.includes(value as Theme)
}

function getSystemTheme(): ResolvedTheme {
  if (window.matchMedia(COLOR_SCHEME_QUERY).matches) {
    return "dark"
  }

  return "light"
}

/**
 * Desliga as transições durante a troca de tema por uma classe definida em index.css.
 * custom: o template injetava um <style> inline, que a CSP estrita (style-src 'self') bloqueia.
 */
function disableTransitionsTemporarily() {
  const root = document.documentElement
  root.classList.add("theme-switching")

  return () => {
    window.getComputedStyle(document.body)
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        root.classList.remove("theme-switching")
      })
    })
  }
}

/** Último toque ou clique: a troca de tema cresce a partir dele (sem toque, do centro da tela). */
let lastPointer: { x: number; y: number } | null = null

/**
 * Troca o tema com um círculo que cresce de onde a pessoa tocou (view transition, index.css). Sem suporte no
 * navegador ou com movimento reduzido, troca direto. As coordenadas vão por propriedades do CSSOM (a CSP só barra
 * estilo inline no HTML).
 */
function revealTheme(update: () => void) {
  const root = document.documentElement
  const { innerWidth: width, innerHeight: height } = window
  const { x, y } = lastPointer ?? { x: width / 2, y: height / 2 }
  const radius = Math.hypot(Math.max(x, width - x), Math.max(y, height - y))
  root.style.setProperty("--theme-reveal-x", `${x}px`)
  root.style.setProperty("--theme-reveal-y", `${y}px`)
  root.style.setProperty("--theme-reveal-r", `${radius}px`)
  root.dataset.themeReveal = ""
  const transition = document.startViewTransition(update)
  void transition.finished.finally(() => {
    delete root.dataset.themeReveal
  })
}

/**
 * Muda outra coisa da aparência (a paleta de cor) com o mesmo círculo da troca de tema. Sem suporte no navegador ou
 * com movimento reduzido, muda direto. O update precisa deixar o DOM pronto (flushSync no estado do React).
 */
export function revealAppearance(update: () => void) {
  if (
    prefersReducedMotion() ||
    typeof document.startViewTransition !== "function"
  ) {
    update()
    return
  }
  revealTheme(update)
}

export function ThemeProvider({
  children,
  defaultTheme = "system",
  storageKey = "theme",
  disableTransitionOnChange = true,
  ...props
}: ThemeProviderProps) {
  const [theme, setThemeState] = React.useState<Theme>(() => {
    const storedTheme = localStorage.getItem(storageKey)
    if (isTheme(storedTheme)) {
      return storedTheme
    }

    return defaultTheme
  })

  const applyTheme = React.useCallback(
    (nextTheme: Theme) => {
      const root = document.documentElement
      const resolvedTheme =
        nextTheme === "system" ? getSystemTheme() : nextTheme
      const restoreTransitions = disableTransitionOnChange
        ? disableTransitionsTemporarily()
        : null

      root.classList.remove("light", "dark")
      root.classList.add(resolvedTheme)

      if (restoreTransitions) {
        restoreTransitions()
      }
    },
    [disableTransitionOnChange]
  )

  const setTheme = React.useCallback(
    (nextTheme: Theme) => {
      localStorage.setItem(storageKey, nextTheme)
      const resolved = nextTheme === "system" ? getSystemTheme() : nextTheme
      const changes = !document.documentElement.classList.contains(resolved)
      if (
        !changes ||
        prefersReducedMotion() ||
        typeof document.startViewTransition !== "function"
      ) {
        setThemeState(nextTheme)
        return
      }
      revealTheme(() => {
        flushSync(() => setThemeState(nextTheme))
        applyTheme(nextTheme)
      })
    },
    [storageKey, applyTheme]
  )

  React.useEffect(() => {
    const remember = (event: PointerEvent) => {
      lastPointer = { x: event.clientX, y: event.clientY }
    }
    window.addEventListener("pointerdown", remember, { capture: true })
    return () =>
      window.removeEventListener("pointerdown", remember, { capture: true })
  }, [])

  React.useEffect(() => {
    applyTheme(theme)

    if (theme !== "system") {
      return undefined
    }

    const mediaQuery = window.matchMedia(COLOR_SCHEME_QUERY)
    const handleChange = () => {
      applyTheme("system")
    }

    mediaQuery.addEventListener("change", handleChange)

    return () => {
      mediaQuery.removeEventListener("change", handleChange)
    }
  }, [theme, applyTheme])

  React.useEffect(() => {
    const handleStorageChange = (event: StorageEvent) => {
      if (event.storageArea !== localStorage) {
        return
      }

      if (event.key !== storageKey) {
        return
      }

      if (isTheme(event.newValue)) {
        setThemeState(event.newValue)
        return
      }

      setThemeState(defaultTheme)
    }

    window.addEventListener("storage", handleStorageChange)

    return () => {
      window.removeEventListener("storage", handleStorageChange)
    }
  }, [defaultTheme, storageKey])

  const value = React.useMemo(
    () => ({
      theme,
      setTheme,
    }),
    [theme, setTheme]
  )

  return (
    <ThemeProviderContext.Provider {...props} value={value}>
      {children}
    </ThemeProviderContext.Provider>
  )
}

export const useTheme = () => {
  const context = React.useContext(ThemeProviderContext)

  if (context === undefined) {
    throw new Error("useTheme must be used within a ThemeProvider")
  }

  return context
}
