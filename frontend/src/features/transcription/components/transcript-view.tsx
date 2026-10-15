import { useEffect, useRef, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { CaptionsIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import { errorMessage } from "@/lib/api-client"
import { transcriptionQueries, useTranscribe } from "../api"

/**
 * Transcrição de um áudio (AU-03): mostra a do cache; senão, "Transcrever" sob demanda ou automaticamente
 * para quem recebe, se a pessoa ligou essa opção. No navegador (AU-04), mostra o download do modelo.
 */
export function TranscriptView({
  mediaId,
  received,
}: {
  mediaId: string
  received: boolean
}) {
  const { t, i18n } = useTranslation()
  const transcript = useQuery(transcriptionQueries.transcript(mediaId))
  const options = useQuery(transcriptionQueries.options())
  const transcribe = useTranscribe(mediaId)
  const [progress, setProgress] = useState<number | null>(null)
  const autoStarted = useRef(false)

  const run = () => {
    setProgress(null)
    transcribe.mutate((fraction) => setProgress(fraction))
  }

  useEffect(() => {
    if (
      received &&
      !autoStarted.current &&
      options.data?.preference.auto &&
      transcript.isSuccess &&
      transcript.data === null
    ) {
      autoStarted.current = true
      transcribe.mutate((fraction) => setProgress(fraction))
    }
  }, [
    received,
    options.data,
    transcript.isSuccess,
    transcript.data,
    transcribe,
  ])

  if (transcript.data) {
    return (
      <p className="text-sm whitespace-pre-wrap italic opacity-90">
        <span className="sr-only">{t("transcription.label")}: </span>
        {transcript.data.text || t("transcription.empty")}
      </p>
    )
  }
  if (transcribe.isPending) {
    return (
      <p
        className="flex items-center gap-2 text-xs"
        role="status"
        aria-live="polite"
      >
        <Spinner />
        {progress !== null && progress < 1
          ? t("transcription.downloading", {
              percent: new Intl.NumberFormat(i18n.language, {
                style: "percent",
              }).format(progress),
            })
          : t("transcription.working")}
      </p>
    )
  }
  return (
    <span className="flex flex-col items-start gap-1">
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={(event) => {
          event.stopPropagation()
          run()
        }}
      >
        <CaptionsIcon data-icon="inline-start" />
        {t("transcription.transcribe")}
      </Button>
      {transcribe.isError ? (
        <span role="alert" className="text-xs text-destructive">
          {errorMessage(transcribe.error, t("transcription.failed"))}
        </span>
      ) : null}
    </span>
  )
}
