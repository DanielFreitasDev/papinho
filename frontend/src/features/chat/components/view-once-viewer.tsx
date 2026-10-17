import { useEffect, useState } from "react"
import { useMutation, useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { EyeOffIcon } from "lucide-react"
import { cn } from "cn"

import { Alert, AlertDescription } from "@/components/ui/alert"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { authQueries } from "@/features/auth"
import { LocalAudioPlayer, VideoPlayer } from "@/features/media"
import { apiFetch, errorMessage } from "@/lib/api-client"

type Grant = { token: string; kind: string; contentType: string }

/**
 * Abre uma mensagem de visualização única (MS-08): pede o token uma vez, mostra o conteúdo sem baixar,
 * encaminhar, copiar nem menu de contexto, com a marca d'água do @usuario de quem vê e desfoque quando a aba ou
 * a janela perde o foco. O navegador não impede captura de tela; o aviso diz isso.
 */
export function ViewOnceViewer({
  messageId,
  onClose,
}: {
  messageId: string | null
  onClose: () => void
}) {
  const { t } = useTranslation()
  return (
    <Dialog
      open={messageId !== null}
      onOpenChange={(open) => (open ? null : onClose())}
    >
      <DialogContent className="max-w-[min(100vw-2rem,48rem)] gap-3">
        <DialogTitle>{t("viewOnce.title")}</DialogTitle>
        <DialogDescription>{t("viewOnce.screenshotWarning")}</DialogDescription>
        {messageId ? <ViewOnceContent messageId={messageId} /> : null}
      </DialogContent>
    </Dialog>
  )
}

function ViewOnceContent({ messageId }: { messageId: string }) {
  const { t } = useTranslation()
  const me = useQuery(authQueries.me())
  const grant = useMutation({
    mutationFn: () =>
      apiFetch<Grant>(`/api/v1/messages/${messageId}/view-once`, {
        method: "POST",
      }),
  })
  // Já começa desfocado se a janela não tem o foco ou a aba está escondida: o blur que veio antes de abrir não
  // dispara de novo.
  const [hidden, setHidden] = useState(
    () => !document.hasFocus() || document.visibilityState !== "visible"
  )
  const { mutate } = grant

  // Pede o token uma vez ao abrir; ele não é reemitido.
  useEffect(() => {
    mutate()
  }, [mutate, messageId])

  useEffect(() => {
    const hide = () => setHidden(true)
    const show = () => setHidden(document.visibilityState !== "visible")
    const onVisibility = () => setHidden(document.visibilityState !== "visible")
    window.addEventListener("blur", hide)
    window.addEventListener("focus", show)
    document.addEventListener("visibilitychange", onVisibility)
    return () => {
      window.removeEventListener("blur", hide)
      window.removeEventListener("focus", show)
      document.removeEventListener("visibilitychange", onVisibility)
    }
  }, [])

  if (grant.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          {errorMessage(grant.error, t("common.genericError"))}
        </AlertDescription>
      </Alert>
    )
  }
  if (!grant.data) {
    return <Skeleton className="h-64 w-full" />
  }
  const src = `/api/v1/view-once/${grant.data.token}`
  const watermark = `@${me.data?.account.username ?? ""}`
  const kind = grant.data.kind
  return (
    <div
      className="relative overflow-hidden rounded-md bg-black select-none"
      onContextMenu={(event) => event.preventDefault()}
      onCopy={(event) => event.preventDefault()}
      onDragStart={(event) => event.preventDefault()}
      data-testid="view-once-content"
    >
      <div className={cn("transition-[filter]", hidden && "blur-2xl")}>
        {kind === "IMAGE" ? (
          <img
            src={src}
            alt={t("viewOnce.photo")}
            draggable={false}
            className="mx-auto max-h-[70dvh] object-contain"
          />
        ) : kind === "VIDEO" ? (
          <VideoPlayer src={src} label={t("viewOnce.video")} autoPlay />
        ) : (
          <LocalAudioPlayer
            src={src}
            label={t("viewOnce.audio")}
            autoPlay
            className="bg-card p-4 text-card-foreground"
          />
        )}
      </div>
      {/* Marca d'água em SVG (padrão repetido e inclinado) sobre o conteúdo. */}
      <svg
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 size-full"
        data-testid="view-once-watermark"
      >
        <defs>
          <pattern
            id={`watermark-${messageId}`}
            width="200"
            height="90"
            patternUnits="userSpaceOnUse"
            patternTransform="rotate(-20)"
          >
            <text
              x="8"
              y="45"
              fill="#ffffff"
              fillOpacity="0.4"
              fontSize="15"
              fontWeight="600"
            >
              {watermark}
            </text>
          </pattern>
        </defs>
        <rect
          width="100%"
          height="100%"
          fill={`url(#watermark-${messageId})`}
        />
      </svg>
      {hidden ? (
        <div className="absolute inset-0 flex items-center justify-center gap-2 text-sm text-white">
          <EyeOffIcon aria-hidden="true" className="size-5" />
          {t("viewOnce.hidden")}
        </div>
      ) : null}
    </div>
  )
}
