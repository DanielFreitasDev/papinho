import { createFileRoute } from "@tanstack/react-router"

import { TranscriptionSettings } from "@/features/transcription"

export const Route = createFileRoute("/_app/settings/transcription")({
  component: TranscriptionSettings,
})
