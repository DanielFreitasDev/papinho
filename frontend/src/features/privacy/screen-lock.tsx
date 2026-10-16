import { useCallback, useEffect, useRef, useState, type FormEvent } from "react"
import { createPortal } from "react-dom"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { FingerprintIcon, LockIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { apiFetch, errorMessage } from "@/lib/api-client"
import { loginWithPasskey, passkeysSupported } from "@/lib/webauthn"
import { privacyQueries, unlockScreen } from "./api"
import type { ScreenLock } from "./types"

const lockKey = (userId: string) => `papinho:screen-locked:${userId}`

function readLocked(userId: string): boolean {
  try {
    return window.localStorage.getItem(lockKey(userId)) === "1"
  } catch {
    return false
  }
}

function writeLocked(userId: string, locked: boolean) {
  try {
    if (locked) {
      window.localStorage.setItem(lockKey(userId), "1")
    } else {
      window.localStorage.removeItem(lockKey(userId))
    }
  } catch {
    // Sem armazenamento (aba privada): o bloqueio vale só nesta aba.
  }
}

/** O evento corresponde ao atalho salvo ("Alt+Shift+L", "Ctrl+Alt+9")? */
export function matchesShortcut(
  event: Pick<
    KeyboardEvent,
    "altKey" | "ctrlKey" | "shiftKey" | "metaKey" | "code"
  >,
  shortcut: string
): boolean {
  const parts = shortcut.split("+")
  const key = parts.at(-1) ?? ""
  const modifiers = new Set(parts.slice(0, -1))
  const code = /^\d$/.test(key) ? `Digit${key}` : `Key${key}`
  return (
    event.code === code &&
    event.altKey === modifiers.has("Alt") &&
    event.ctrlKey === modifiers.has("Ctrl") &&
    event.shiftKey === modifiers.has("Shift") &&
    event.metaKey === modifiers.has("Meta")
  )
}

/** Atalho no formato do servidor a partir de uma tecla pressionada; nulo sem modificador ou tecla aceita. */
export function shortcutFromEvent(
  event: Pick<
    KeyboardEvent,
    "altKey" | "ctrlKey" | "shiftKey" | "metaKey" | "code"
  >
): string | null {
  const match = /^(?:Key([A-Z])|Digit(\d))$/.exec(event.code)
  if (!match) {
    return null
  }
  const modifiers = [
    event.ctrlKey && "Ctrl",
    event.altKey && "Alt",
    event.shiftKey && "Shift",
    event.metaKey && "Meta",
  ].filter(Boolean)
  if (modifiers.length === 0) {
    return null
  }
  return [...modifiers, match[1] ?? match[2]].join("+")
}

let lockNow: (() => void) | null = null

/** Bloqueia a tela agora (menu e paleta); só tem efeito com o bloqueio configurado. */
export function lockScreenNow() {
  lockNow?.()
}

/**
 * Bloqueio de tela (PV-02). Com o bloqueio configurado, os gatilhos escolhidos (inatividade, trocar de aba ou
 * janela, atalho) cobrem a tela: tudo por trás fica desfocado e inerte até o PIN, a senha ou a passkey. É uma
 * proteção visual: a sessão continua ativa. O estado sobrevive a recarregar e vale para todas as abas.
 */
export function ScreenLockGate({ userId }: { userId: string }) {
  const lock = useQuery(privacyQueries.screenLock())
  const config = lock.data?.enabled ? lock.data : null
  const [locked, setLocked] = useState(() => readLocked(userId))

  const engage = useCallback(() => {
    writeLocked(userId, true)
    setLocked(true)
  }, [userId])
  const release = useCallback(() => {
    writeLocked(userId, false)
    setLocked(false)
  }, [userId])

  useEffect(() => {
    lockNow = config ? engage : null
    return () => {
      lockNow = null
    }
  }, [config, engage])

  // Outras abas bloqueiam e desbloqueiam junto.
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === lockKey(userId)) {
        setLocked(event.newValue === "1")
      }
    }
    window.addEventListener("storage", onStorage)
    return () => window.removeEventListener("storage", onStorage)
  }, [userId])

  useLockTriggers(config, locked, engage)

  if (!locked || !config) {
    return null
  }
  return <LockScreen config={config} userId={userId} onUnlock={release} />
}

function useLockTriggers(
  config: ScreenLock | null,
  locked: boolean,
  engage: () => void
) {
  const lastActivity = useRef(0)
  useEffect(() => {
    if (!config || locked) {
      return
    }
    lastActivity.current = Date.now()
    const touch = () => {
      lastActivity.current = Date.now()
    }
    const events = ["pointerdown", "pointermove", "keydown", "wheel"] as const
    events.forEach((name) =>
      window.addEventListener(name, touch, { passive: true })
    )
    const idle = config.idleMinutes
    const interval = idle
      ? window.setInterval(() => {
          if (Date.now() - lastActivity.current >= idle * 60_000) {
            engage()
          }
        }, 5_000)
      : undefined
    const onVisibility = () => {
      if (config.lockOnHidden && document.visibilityState === "hidden") {
        engage()
      }
    }
    let blurTimer: number | undefined
    const onBlur = () => {
      // Um iframe focado (vídeo incorporado) também tira o foco da janela, mas não dela.
      blurTimer = window.setTimeout(() => {
        if (config.lockOnHidden && !document.hasFocus()) {
          engage()
        }
      }, 300)
    }
    const onKey = (event: KeyboardEvent) => {
      if (config.shortcut && matchesShortcut(event, config.shortcut)) {
        event.preventDefault()
        engage()
      }
    }
    document.addEventListener("visibilitychange", onVisibility)
    window.addEventListener("blur", onBlur)
    window.addEventListener("keydown", onKey)
    return () => {
      events.forEach((name) => window.removeEventListener(name, touch))
      window.clearInterval(interval)
      window.clearTimeout(blurTimer)
      document.removeEventListener("visibilitychange", onVisibility)
      window.removeEventListener("blur", onBlur)
      window.removeEventListener("keydown", onKey)
    }
  }, [config, locked, engage])
}

/** Marca tudo o que está no <body> (menos a tela de bloqueio) como inerte e desfocado. */
function useInertBackground(overlay: HTMLElement | null) {
  useEffect(() => {
    if (!overlay) {
      return
    }
    const touched = new Set<HTMLElement>()
    const apply = () => {
      Array.from(document.body.children).forEach((child) => {
        if (child instanceof HTMLElement && child !== overlay) {
          child.inert = true
          child.dataset.screenLocked = ""
          touched.add(child)
        }
      })
    }
    apply()
    // Toasts e diálogos abertos depois também ficam por trás.
    const observer = new MutationObserver(apply)
    observer.observe(document.body, { childList: true })
    return () => {
      observer.disconnect()
      touched.forEach((element) => {
        element.inert = false
        delete element.dataset.screenLocked
      })
    }
  }, [overlay])
}

function LockScreen({
  config,
  userId,
  onUnlock,
}: {
  config: ScreenLock
  userId: string
  onUnlock: () => void
}) {
  const { t } = useTranslation()
  const [overlay, setOverlay] = useState<HTMLDivElement | null>(null)
  const [secret, setSecret] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  useInertBackground(overlay)

  useEffect(() => {
    input.current?.focus()
  }, [overlay])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!secret) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await unlockScreen(secret)
      setSecret("")
      onUnlock()
    } catch (failure) {
      setError(errorMessage(failure, t("privacy.screenLock.wrong")))
      setSecret("")
      input.current?.focus()
    } finally {
      setBusy(false)
    }
  }

  async function unlockWithPasskey() {
    setBusy(true)
    setError(null)
    try {
      await loginWithPasskey()
      const me = await apiFetch<{ account: { id: string } }>("/api/v1/me")
      if (me.account.id !== userId) {
        // A passkey era de outra conta: a sessão agora é dela; recarrega no lugar da anterior.
        writeLocked(userId, false)
        window.location.reload()
        return
      }
      onUnlock()
    } catch (failure) {
      setError(errorMessage(failure, t("privacy.screenLock.passkeyFailed")))
    } finally {
      setBusy(false)
    }
  }

  const pin = config.kind === "PIN"
  return createPortal(
    <div
      ref={setOverlay}
      role="dialog"
      aria-modal="true"
      aria-labelledby="screen-lock-title"
      className="fixed inset-0 z-[100] flex items-center justify-center bg-background/80 p-4"
    >
      <form
        onSubmit={(event) => void submit(event)}
        className="flex w-full max-w-xs flex-col items-center gap-4 rounded-xl border bg-card p-6 text-card-foreground shadow-lg"
      >
        <LockIcon aria-hidden="true" className="size-8 text-primary" />
        <h1 id="screen-lock-title" className="text-lg font-semibold">
          {t("privacy.screenLock.lockedTitle")}
        </h1>
        <Field className="w-full" data-invalid={error ? true : undefined}>
          <FieldLabel htmlFor="screen-lock-secret">
            {pin
              ? t("privacy.screenLock.pinLabel")
              : t("privacy.screenLock.passwordLabel")}
          </FieldLabel>
          <Input
            ref={input}
            id="screen-lock-secret"
            type="password"
            inputMode={pin ? "numeric" : undefined}
            autoComplete="off"
            value={secret}
            aria-invalid={error ? true : undefined}
            onChange={(event) => setSecret(event.target.value)}
          />
          {error ? <FieldError>{error}</FieldError> : null}
        </Field>
        <Button type="submit" className="w-full" disabled={busy || !secret}>
          {t("privacy.screenLock.unlock")}
        </Button>
        {config.allowPasskey && passkeysSupported() ? (
          <Button
            type="button"
            variant="outline"
            className="w-full"
            disabled={busy}
            onClick={() => void unlockWithPasskey()}
          >
            <FingerprintIcon />
            {t("privacy.screenLock.withPasskey")}
          </Button>
        ) : null}
        <p className="text-center text-xs text-muted-foreground">
          {t("privacy.screenLock.visualOnly")}
        </p>
      </form>
    </div>,
    document.body
  )
}
