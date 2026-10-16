import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { CloudOffIcon, LoaderCircleIcon } from "lucide-react"

import { realtime, type ConnectionState } from "@/lib/realtime"

/**
 * Estado da conexão sempre visível quando não está tudo certo (NT-05, UI-03): sem rede, as conversas recentes
 * vêm do aparelho e os textos esperam na fila; reconectando, avisa até voltar. Espera um instante antes de
 * aparecer, para não piscar na abertura.
 */
export function ConnectionBanner() {
  const { t } = useTranslation()
  const [state, setState] = useState<ConnectionState>(realtime.currentState())
  const [online, setOnline] = useState(navigator.onLine)
  const [settled, setSettled] = useState(false)

  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(true), 2_000)
    const offState = realtime.onState((next) => setState(next))
    const update = () => setOnline(navigator.onLine)
    window.addEventListener("online", update)
    window.addEventListener("offline", update)
    return () => {
      window.clearTimeout(timer)
      offState()
      window.removeEventListener("online", update)
      window.removeEventListener("offline", update)
    }
  }, [])

  const offline = !online || (settled && state === "offline")
  const reconnecting = online && settled && state === "connecting"
  return (
    <div role="status" aria-live="polite">
      {offline ? (
        <p className="flex items-center gap-2 border-b bg-muted px-3 pt-[max(0.5rem,env(safe-area-inset-top))] pb-2 text-sm">
          <CloudOffIcon aria-hidden="true" className="size-4 shrink-0" />
          {t("connection.offline")}
        </p>
      ) : reconnecting ? (
        <p className="flex items-center gap-2 border-b bg-muted px-3 pt-[max(0.5rem,env(safe-area-inset-top))] pb-2 text-sm">
          <LoaderCircleIcon
            aria-hidden="true"
            className="size-4 shrink-0 animate-spin motion-reduce:animate-none"
          />
          {t("connection.reconnecting")}
        </p>
      ) : null}
    </div>
  )
}
