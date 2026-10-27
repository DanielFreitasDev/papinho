import { type Ref, useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { cn } from "cn"
import {
  ChevronLeftIcon,
  ChevronUpIcon,
  LockIcon,
  MicIcon,
  SendHorizontalIcon,
  SquareIcon,
  Trash2Icon,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { toast } from "@/components/ui/toast"
import {
  formatDuration,
  LocalAudioPlayer,
  measureAudioDuration,
} from "@/features/media"
import { motionMs, prefersReducedMotion } from "@/lib/motion"
import { realtime } from "@/lib/realtime"
import {
  BAR_MS,
  barsFor,
  levelFromSamples,
  waveformBars,
} from "../recording-wave"

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
/** Arrastar para a esquerda enquanto segura descarta, como no WhatsApp. */
const CANCEL_DISTANCE = 120
const ACTIVITY_INTERVAL = 3_000
/** Barras guardadas enquanto ainda não há onda na tela (segurando): bastam para a onda mais larga ao travar. */
const MAX_BARS = 240

type Recording = {
  recorder: MediaRecorder
  stream: MediaStream
  chunks: Blob[]
  extension: string
  startedAt: number
  context: AudioContext
  analyser: AnalyserNode
}

type Phase = "idle" | "recording" | "preview" | "discarding"

/**
 * De onde o microfone salta para a lixeira: "side" quando a lixeira é o botão ao lado do indicador (gravação travada
 * e pré-escuta), "hold" quando ela aparece no lugar do indicador (segurando e deslizando para cancelar).
 */
type DiscardFrom = "side" | "hold"

/**
 * Gravação de mensagem de voz (AU-01) com MediaRecorder, como no WhatsApp: microfone pulsando com o volume, tempo e
 * forma de onda ao vivo, gravação travada (toque ou clique) ou segurando o botão (arrastar para cima trava, para a
 * esquerda descarta), pré-escuta antes de enviar e descarte com a lixeira animada. Avisa "gravando áudio…" (MS-03).
 */
export function VoiceRecorder({
  conversationId,
  disabled,
  animateIn,
  onActiveChange,
  onSend,
}: {
  conversationId: string
  disabled: boolean
  /** O microfone chega com o "pop" (troca com o botão de enviar ou volta da gravação). */
  animateIn: boolean
  onActiveChange: (active: boolean) => void
  onSend: (file: File) => void
}) {
  const { t } = useTranslation()
  const [phase, setPhase] = useState<Phase>("idle")
  const [locked, setLocked] = useState(true)
  const [elapsed, setElapsed] = useState(0)
  const [discardFrom, setDiscardFrom] = useState<DiscardFrom>("side")
  const [announcement, setAnnouncement] = useState("")
  const [preview, setPreview] = useState<{
    file: File
    url: string
    durationMs: number | null
  } | null>(null)
  const recording = useRef<Recording | null>(null)
  const canvas = useRef<HTMLCanvasElement>(null)
  const halo = useRef<HTMLSpanElement>(null)
  const hint = useRef<HTMLSpanElement>(null)
  const lock = useRef<HTMLSpanElement>(null)
  const trash = useRef<HTMLDivElement>(null)
  const micButton = useRef<HTMLButtonElement>(null)
  const suppressClick = useRef(false)
  const afterStop = useRef<"send" | "preview" | "discard">("preview")
  // Soltar antes de o microfone abrir: a parada fica pendente até a gravação começar.
  const pendingStop = useRef<"send" | "preview" | "discard" | null>(null)
  // O botão que tinha o foco some com a troca de fase; de volta ao microfone, o foco vai para ele.
  const refocus = useRef(false)

  useEffect(() => {
    onActiveChange(phase !== "idle")
  }, [phase, onActiveChange])

  useEffect(() => {
    if (phase !== "idle") {
      refocus.current = true
    } else if (refocus.current) {
      refocus.current = false
      const active = document.activeElement
      if (!active || active === document.body) {
        micButton.current?.focus({ preventScroll: true })
      }
    }
  }, [phase])

  // Tempo, volume, forma de onda e "gravando áudio…" enquanto grava.
  useEffect(() => {
    if (phase !== "recording") {
      return
    }
    const reduced = prefersReducedMotion()
    const samples = new Uint8Array(512)
    const levels: number[] = []
    let peak = 0
    let lastBar = performance.now()
    let lastActivity = 0
    let frame = 0
    // Um relógio só: o tempo do quadro do requestAnimationFrame pode andar diferente do performance.now() (a linha do
    // tempo das animações pode ser desacelerada), e misturar os dois atrasava as barras.
    const draw = () => {
      const now = performance.now()
      const current = recording.current
      if (current) {
        setElapsed(Math.floor((Date.now() - current.startedAt) / 1000))
        current.analyser.getByteTimeDomainData(samples)
        const level = levelFromSamples(samples)
        peak = Math.max(peak, level)
        // O halo do microfone acompanha a voz; com movimento reduzido, fica parado.
        if (!reduced && halo.current) {
          halo.current.style.transform = `scale(${1 + level * 0.8})`
        }
        if (now - lastBar >= BAR_MS) {
          levels.unshift(peak)
          peak = 0
          lastBar = now
        }
        const target = canvas.current
        levels.length = Math.min(
          levels.length,
          target ? barsFor(target.clientWidth) : MAX_BARS
        )
        if (target) {
          paintWave(target, levels, reduced ? 0 : (now - lastBar) / BAR_MS)
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

  // A lixeira avisa o fim pela animação. Se ela não chegar ao fim (aba escondida, animação que não rodou), passado o
  // tempo dela volta ao microfone; enquanto ainda anima (mais lenta, por exemplo), espera.
  useEffect(() => {
    if (phase !== "discarding") {
      return
    }
    let timer = 0
    const check = () => {
      const running = trash.current
        ?.getAnimations?.({ subtree: true })
        .some((animation) => animation.playState === "running")
      if (running) {
        timer = window.setTimeout(check, 250)
      } else {
        setPhase("idle")
      }
    }
    timer = window.setTimeout(check, motionMs("long") + 250)
    return () => window.clearTimeout(timer)
  }, [phase])

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
      analyser.fftSize = 512
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
      setAnnouncement("")
      const pending = pendingStop.current
      if (pending) {
        // Soltou ou cancelou antes de o microfone abrir: para sem mostrar a gravação.
        pendingStop.current = null
        stop(pending)
        return
      }
      setLocked(lockedMode)
      setElapsed(0)
      setPhase("recording")
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
      // A lixeira, se estiver animando, volta ao microfone quando terminar.
      setPhase((now) => (now === "discarding" ? now : "idle"))
    } else if (afterStop.current === "send") {
      onSend(file)
      setPhase("idle")
    } else {
      const url = URL.createObjectURL(file)
      setPreview({ file, url, durationMs: null })
      setPhase("preview")
      // O WebM gravado não diz a duração ao elemento: medida aqui, a pré-escuta já mostra o total (AU-02).
      void measureAudioDuration(file).then((durationMs) =>
        setPreview((now) => (now?.url === url ? { ...now, durationMs } : now))
      )
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

  /** Descarta a gravação ou a pré-escuta: a lixeira anima (com movimento reduzido, volta direto ao microfone). */
  function discard(from: DiscardFrom) {
    setAnnouncement(t("audio.discarded"))
    if (preview) {
      URL.revokeObjectURL(preview.url)
      setPreview(null)
    } else {
      stop("discard")
    }
    if (prefersReducedMotion()) {
      setPhase("idle")
    } else {
      setDiscardFrom(from)
      setPhase("discarding")
    }
  }

  function sendPreview() {
    if (preview) {
      onSend(preview.file)
      URL.revokeObjectURL(preview.url)
    }
    setPreview(null)
    setPhase("idle")
  }

  // Região viva fixa: o aviso do descarte é lido mesmo quando a lixeira (decorativa) some.
  const status = (
    <span role="status" className="sr-only">
      {announcement}
    </span>
  )

  if (phase === "discarding") {
    return (
      <>
        {status}
        <div
          ref={trash}
          aria-hidden="true"
          data-trash={discardFrom}
          className="flex min-w-0 flex-1 items-center"
          onAnimationEnd={(event) => {
            if (event.animationName === "trash-can") {
              setPhase("idle")
            }
          }}
        >
          <span
            className={cn(
              "relative inline-flex shrink-0 items-center justify-center",
              discardFrom === "side" ? "size-11" : "h-11 w-6"
            )}
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
              className="trash-can size-6 overflow-visible text-muted-foreground"
            >
              <g className="trash-lid">
                <path d="M3 6h18" />
                <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
              </g>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
              <path d="M10 11v6" />
              <path d="M14 11v6" />
            </svg>
            <MicIcon
              className={cn(
                "trash-mic absolute top-1/2 size-4 -translate-y-1/2 text-destructive",
                discardFrom === "side" ? "left-14" : "left-1"
              )}
            />
          </span>
        </div>
      </>
    )
  }

  if (phase === "preview" && preview) {
    return (
      <>
        {status}
        <div className="flex min-w-0 flex-1 animate-in items-center gap-2 duration-(--duration-base) ease-(--ease-enter) zoom-in-95 fade-in">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="size-11 rounded-full"
            aria-label={t("audio.discard")}
            onClick={() => discard("side")}
          >
            <Trash2Icon />
          </Button>
          <LocalAudioPlayer
            src={preview.url}
            label={t("audio.preview")}
            durationMs={preview.durationMs}
            className="min-w-0 flex-1"
          />
          <Button
            type="button"
            size="icon"
            className="size-11 rounded-full"
            aria-label={t("audio.send")}
            onClick={sendPreview}
          >
            <SendHorizontalIcon />
          </Button>
        </div>
      </>
    )
  }

  if (phase === "recording" && locked) {
    return (
      <>
        {status}
        <div
          className="flex min-w-0 flex-1 animate-in items-center gap-2 duration-(--duration-base) ease-(--ease-enter) fade-in slide-in-from-right-8"
          role="group"
          aria-label={t("audio.recording")}
        >
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="size-11 rounded-full"
            aria-label={t("audio.cancel")}
            onClick={() => discard("side")}
          >
            <Trash2Icon />
          </Button>
          <RecordingIndicator halo={halo} />
          <span className="text-sm tabular-nums" aria-live="off">
            {formatDuration(elapsed)}
          </span>
          <canvas
            ref={canvas}
            aria-hidden="true"
            className="h-8 min-w-0 flex-1 text-primary"
          />
          <LockIcon
            role="img"
            aria-label={t("audio.locked")}
            className="size-4 shrink-0 text-muted-foreground"
          />
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="size-11 rounded-full"
            aria-label={t("audio.stop")}
            onClick={() => stop("preview")}
          >
            <SquareIcon />
          </Button>
          <Button
            type="button"
            size="icon"
            className="size-11 animate-pop rounded-full"
            aria-label={t("audio.send")}
            onClick={() => stop("send")}
          >
            <SendHorizontalIcon />
          </Button>
        </div>
      </>
    )
  }

  if (phase === "recording") {
    // Segurando (toque): o dedo está sobre o microfone, que cresce e pulsa; à esquerda, deslizar para cancelar; acima,
    // a trava. Os gestos vêm dos ouvintes da janela postos no toque.
    return (
      <>
        {status}
        <div
          className="relative flex min-w-0 flex-1 animate-in items-center gap-2 duration-(--duration-base) ease-(--ease-enter) fade-in slide-in-from-right-8"
          role="group"
          aria-label={t("audio.recording")}
        >
          <RecordingIndicator />
          <span className="text-sm tabular-nums" aria-live="off">
            {formatDuration(elapsed)}
          </span>
          <span className="flex min-w-0 flex-1 justify-center overflow-hidden">
            <span
              ref={hint}
              className="flex min-w-0 items-center gap-1 text-sm text-muted-foreground"
            >
              <ChevronLeftIcon
                aria-hidden="true"
                className="size-4 shrink-0 animate-nudge"
              />
              <span className="truncate">{t("audio.slideToCancel")}</span>
            </span>
          </span>
          <span className="sr-only">{t("audio.slideToLock")}</span>
          <span
            aria-hidden="true"
            className="relative inline-flex size-11 shrink-0 animate-pop items-center justify-center"
          >
            <span className="absolute inset-0 animate-mic-ring rounded-full bg-destructive" />
            <span className="absolute inset-0 animate-mic-ring-late rounded-full bg-destructive" />
            <span
              ref={halo}
              className="absolute inset-0 rounded-full bg-destructive transition-transform duration-(--duration-instant)"
            />
            <MicIcon className="relative size-5 text-white" />
            <span className="absolute bottom-full mb-3 animate-in duration-(--duration-base) ease-(--ease-enter) fade-in slide-in-from-bottom-2">
              <span
                ref={lock}
                className="flex flex-col items-center gap-0.5 rounded-full border bg-background px-1.5 py-2 text-muted-foreground shadow-sm"
              >
                <LockIcon className="size-4" />
                <ChevronUpIcon className="size-3.5 animate-nudge-up" />
              </span>
            </span>
          </span>
        </div>
      </>
    )
  }

  return (
    <>
      {status}
      {/* Dentro da pílula do campo, como no WhatsApp: a bola acende com o mouse em cima e ao pressionar. */}
      <Button
        ref={micButton}
        type="button"
        variant="ball"
        size="icon"
        aria-label={t("audio.record")}
        disabled={disabled}
        className={cn(
          "size-10 touch-none select-none",
          animateIn && "animate-pop"
        )}
        onContextMenu={(event) => event.preventDefault()}
        onKeyDown={() => {
          suppressClick.current = false
        }}
        onPointerDown={(event) => {
          // Toque longo com deslize (cancelar ou travar) não gera clique: o aviso de ignorar o clique vale só até a
          // próxima interação, senão o toque seguinte no microfone se perdia.
          suppressClick.current = false
          if (event.button !== 0 || event.pointerType === "mouse") {
            return
          }
          // Toque longo (toque ou caneta): grava enquanto segura; soltar envia, arrastar para cima trava e para a
          // esquerda descarta. Os eventos seguintes vêm da janela, porque o botão some quando a gravação começa.
          const x = event.clientX
          const y = event.clientY
          const state = { hold: false, locked: false, cancelled: false }
          const timer = window.setTimeout(() => {
            state.hold = true
            suppressClick.current = true
            void start(false)
          }, HOLD_MS)
          const move = (moveEvent: PointerEvent) => {
            if (!state.hold || state.locked || state.cancelled) {
              return
            }
            const left = Math.max(0, x - moveEvent.clientX)
            const up = Math.max(0, y - moveEvent.clientY)
            // O aviso acompanha o dedo e some perto do limite; a trava sobe junto.
            if (hint.current) {
              hint.current.style.transform = `translateX(${-left}px)`
              hint.current.style.opacity = String(
                1 - Math.min(1, left / (CANCEL_DISTANCE * 0.75))
              )
            }
            if (lock.current) {
              lock.current.style.transform = `translateY(${-Math.min(up, LOCK_DISTANCE)}px)`
            }
            if (up > LOCK_DISTANCE) {
              state.locked = true
              setLocked(true)
            } else if (left > CANCEL_DISTANCE) {
              state.cancelled = true
              discard("hold")
            }
          }
          const end = (endEvent: PointerEvent) => {
            window.clearTimeout(timer)
            window.removeEventListener("pointermove", move)
            window.removeEventListener("pointerup", end)
            window.removeEventListener("pointercancel", end)
            if (state.hold && !state.locked && !state.cancelled) {
              if (endEvent.type === "pointercancel") {
                discard("hold")
              } else {
                stop("send")
              }
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
    </>
  )
}

/** Microfone vermelho piscando ao lado do tempo; o halo cresce com o volume (o ref recebe o tamanho a cada quadro). */
function RecordingIndicator({ halo }: { halo?: Ref<HTMLSpanElement> }) {
  return (
    <span
      aria-hidden="true"
      className="relative inline-flex size-6 shrink-0 items-center justify-center"
    >
      <span
        ref={halo}
        className="absolute inset-0.5 rounded-full bg-destructive/20 transition-transform duration-(--duration-instant)"
      />
      <MicIcon className="relative size-4 animate-rec-blink text-destructive" />
    </span>
  )
}

/** Desenha a onda no canvas na resolução da tela, com a cor do texto do próprio canvas. */
function paintWave(
  target: HTMLCanvasElement,
  levels: readonly number[],
  progress: number
) {
  const context = target.getContext("2d")
  if (!context) {
    return
  }
  const ratio = window.devicePixelRatio || 1
  const width = target.clientWidth
  const height = target.clientHeight
  const pixelWidth = Math.round(width * ratio)
  const pixelHeight = Math.round(height * ratio)
  if (target.width !== pixelWidth || target.height !== pixelHeight) {
    target.width = pixelWidth
    target.height = pixelHeight
  }
  context.setTransform(ratio, 0, 0, ratio, 0, 0)
  context.clearRect(0, 0, width, height)
  context.fillStyle = getComputedStyle(target).color
  for (const bar of waveformBars(levels, width, height, progress)) {
    context.beginPath()
    context.roundRect(bar.x, bar.y, bar.width, bar.height, bar.width / 2)
    context.fill()
  }
}
