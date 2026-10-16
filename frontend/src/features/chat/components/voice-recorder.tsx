import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  LockIcon,
  MicIcon,
  SendHorizontalIcon,
  SquareIcon,
  Trash2Icon,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { toast } from "@/components/ui/toast"
import { formatDuration, LocalAudioPlayer } from "@/features/media"
import { realtime } from "@/lib/realtime"

/** Formatos na ordem de preferência: Opus em WebM/Ogg; AAC em MP4 no Safari (AU-01). */
const FORMATS = [
  { mime: "audio/webm;codecs=opus", extension: "webm" },
  { mime: "audio/ogg;codecs=opus", extension: "ogg" },
  { mime: "audio/mp4", extension: "m4a" },
] as const

/** Segurar o microfone por mais que isso grava enquanto segura; um toque grava travado. */
const HOLD_MS = 350
/** Arrastar para cima enquanto segura trava a gravação. */
const LOCK_DISTANCE = 60
const ACTIVITY_INTERVAL = 3_000

type Recording = {
  recorder: MediaRecorder
  stream: MediaStream
  chunks: Blob[]
  extension: string
  startedAt: number
  context: AudioContext
  analyser: AnalyserNode
}

type Phase = "idle" | "recording" | "preview"

/**
 * Gravação de mensagem de voz (AU-01) com MediaRecorder: forma de onda ao vivo, gravação travada (toque) ou
 * segurando o botão (arrastar para cima trava), pré-escuta antes de enviar e cancelamento. Avisa "gravando
 * áudio…" (MS-03).
 */
export function VoiceRecorder({
  conversationId,
  disabled,
  onActiveChange,
  onSend,
}: {
  conversationId: string
  disabled: boolean
  onActiveChange: (active: boolean) => void
  onSend: (file: File) => void
}) {
  const { t } = useTranslation()
  const [phase, setPhase] = useState<Phase>("idle")
  const [locked, setLocked] = useState(true)
  const [elapsed, setElapsed] = useState(0)
  const [preview, setPreview] = useState<{ file: File; url: string } | null>(
    null
  )
  const recording = useRef<Recording | null>(null)
  const canvas = useRef<HTMLCanvasElement>(null)
  const suppressClick = useRef(false)
  const afterStop = useRef<"send" | "preview" | "discard">("preview")
  // Soltar antes de o microfone abrir: a parada fica pendente até a gravação começar.
  const pendingStop = useRef<"send" | "preview" | "discard" | null>(null)

  useEffect(() => {
    onActiveChange(phase !== "idle")
  }, [phase, onActiveChange])

  // Tempo, forma de onda e "gravando áudio…" enquanto grava.
  useEffect(() => {
    if (phase !== "recording") {
      return
    }
    let frame = 0
    let lastActivity = 0
    const data = new Uint8Array(64)
    const draw = () => {
      const current = recording.current
      const target = canvas.current
      if (current && target) {
        setElapsed((Date.now() - current.startedAt) / 1000)
        current.analyser.getByteFrequencyData(data)
        const context = target.getContext("2d")
        if (context) {
          const color = getComputedStyle(target).color
          context.clearRect(0, 0, target.width, target.height)
          context.fillStyle = color
          const bar = target.width / data.length
          data.forEach((value, index) => {
            const height = Math.max(2, (value / 255) * target.height)
            context.fillRect(
              index * bar,
              (target.height - height) / 2,
              bar * 0.6,
              height
            )
          })
        }
        if (Date.now() - lastActivity > ACTIVITY_INTERVAL) {
          realtime.activity(conversationId, "recording")
          lastActivity = Date.now()
        }
      }
      frame = requestAnimationFrame(draw)
    }
    frame = requestAnimationFrame(draw)
    return () => {
      cancelAnimationFrame(frame)
      realtime.activity(conversationId, "")
    }
  }, [phase, conversationId])

  useEffect(
    () => () => {
      recording.current?.stream.getTracks().forEach((track) => track.stop())
      void recording.current?.context.close()
    },
    []
  )

  async function start(lockedMode: boolean) {
    const format = FORMATS.find((item) =>
      MediaRecorder.isTypeSupported(item.mime)
    )
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const recorder = new MediaRecorder(
        stream,
        format ? { mimeType: format.mime } : undefined
      )
      const context = new AudioContext()
      const analyser = context.createAnalyser()
      analyser.fftSize = 128
      context.createMediaStreamSource(stream).connect(analyser)
      const current: Recording = {
        recorder,
        stream,
        chunks: [],
        extension: format?.extension ?? "webm",
        startedAt: Date.now(),
        context,
        analyser,
      }
      recorder.addEventListener("dataavailable", (event) => {
        if (event.data.size > 0) {
          current.chunks.push(event.data)
        }
      })
      recorder.addEventListener("stop", () => finish(current))
      recording.current = current
      recorder.start(250)
      setLocked(lockedMode)
      setElapsed(0)
      setPhase("recording")
      if (pendingStop.current) {
        stop(pendingStop.current)
        pendingStop.current = null
      }
    } catch {
      toast.add({ title: t("audio.microphoneDenied"), type: "error" })
    }
  }

  function finish(current: Recording) {
    current.stream.getTracks().forEach((track) => track.stop())
    void current.context.close()
    recording.current = null
    const type = current.recorder.mimeType || "audio/webm"
    const blob = new Blob(current.chunks, { type })
    const name = `audio-${new Date().toISOString().replace(/[:.]/g, "-")}.${current.extension}`
    const file = new File([blob], name, { type: type.split(";")[0] })
    if (afterStop.current === "discard" || blob.size === 0) {
      setPhase("idle")
    } else if (afterStop.current === "send") {
      onSend(file)
      setPhase("idle")
    } else {
      setPreview({ file, url: URL.createObjectURL(file) })
      setPhase("preview")
    }
  }

  function stop(then: "send" | "preview" | "discard") {
    afterStop.current = then
    const current = recording.current
    if (!current) {
      pendingStop.current = then
    } else if (current.recorder.state !== "inactive") {
      current.recorder.stop()
    }
  }

  function closePreview(send: boolean) {
    if (preview) {
      if (send) {
        onSend(preview.file)
      }
      URL.revokeObjectURL(preview.url)
    }
    setPreview(null)
    setPhase("idle")
  }

  if (phase === "preview" && preview) {
    return (
      <div className="flex flex-1 items-center gap-2">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t("audio.discard")}
          onClick={() => closePreview(false)}
        >
          <Trash2Icon />
        </Button>
        <LocalAudioPlayer
          src={preview.url}
          label={t("audio.preview")}
          className="min-w-0 flex-1"
        />
        <Button
          type="button"
          size="icon"
          aria-label={t("audio.send")}
          onClick={() => closePreview(true)}
        >
          <SendHorizontalIcon />
        </Button>
      </div>
    )
  }

  if (phase === "recording") {
    return (
      <div
        className="flex flex-1 items-center gap-2"
        role="group"
        aria-label={t("audio.recording")}
      >
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t("audio.cancel")}
          onClick={() => stop("discard")}
        >
          <Trash2Icon />
        </Button>
        <span
          aria-hidden="true"
          className="size-2.5 shrink-0 animate-pulse rounded-full bg-destructive motion-reduce:animate-none"
        />
        <span className="text-sm tabular-nums" aria-live="off">
          {formatDuration(elapsed)}
        </span>
        <canvas
          ref={canvas}
          width={160}
          height={32}
          aria-hidden="true"
          className="h-8 min-w-0 flex-1 text-primary"
        />
        {locked ? (
          <>
            <LockIcon
              role="img"
              aria-label={t("audio.locked")}
              className="size-4 shrink-0 text-muted-foreground"
            />
            <Button
              type="button"
              variant="outline"
              size="icon"
              aria-label={t("audio.stop")}
              onClick={() => stop("preview")}
            >
              <SquareIcon />
            </Button>
            <Button
              type="button"
              size="icon"
              aria-label={t("audio.send")}
              onClick={() => stop("send")}
            >
              <SendHorizontalIcon />
            </Button>
          </>
        ) : (
          <span className="text-xs text-muted-foreground">
            {t("audio.slideToLock")}
          </span>
        )}
      </div>
    )
  }

  return (
    <Button
      type="button"
      size="icon"
      aria-label={t("audio.record")}
      disabled={disabled}
      onPointerDown={(event) => {
        if (event.button !== 0 || event.pointerType === "mouse") {
          return
        }
        // Toque longo (toque ou caneta): grava enquanto segura; soltar envia, arrastar para cima trava. Os
        // eventos seguintes vêm da janela, porque o botão some quando a gravação começa.
        const y = event.clientY
        const state = { hold: false, locked: false }
        const timer = window.setTimeout(() => {
          state.hold = true
          suppressClick.current = true
          void start(false)
        }, HOLD_MS)
        const move = (moveEvent: PointerEvent) => {
          if (
            state.hold &&
            !state.locked &&
            y - moveEvent.clientY > LOCK_DISTANCE
          ) {
            state.locked = true
            setLocked(true)
          }
        }
        const end = (endEvent: PointerEvent) => {
          window.clearTimeout(timer)
          window.removeEventListener("pointermove", move)
          window.removeEventListener("pointerup", end)
          window.removeEventListener("pointercancel", end)
          if (state.hold && !state.locked) {
            stop(endEvent.type === "pointercancel" ? "discard" : "send")
          }
        }
        window.addEventListener("pointermove", move)
        window.addEventListener("pointerup", end)
        window.addEventListener("pointercancel", end)
      }}
      onClick={() => {
        // Clique, toque curto ou teclado: grava travado.
        if (suppressClick.current) {
          suppressClick.current = false
          return
        }
        if (!recording.current) {
          void start(true)
        }
      }}
    >
      <MicIcon />
    </Button>
  )
}
