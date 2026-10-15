import { useSyncExternalStore } from "react"

/**
 * "digitando…" e "gravando áudio…" recebidos pelo WebSocket (MS-03). Efêmero: some sozinho se o aviso de parada
 * não chegar.
 */
export type Activity = { userId: string; activity: "typing" | "recording" }

const TTL = 6_000
const state = new Map<string, Activity & { until: number }>()
const listeners = new Set<() => void>()
let timer: number | undefined

function emit() {
  listeners.forEach((listener) => listener())
}

function schedule() {
  window.clearTimeout(timer)
  const next = Math.min(...[...state.values()].map((entry) => entry.until))
  if (Number.isFinite(next)) {
    timer = window.setTimeout(expire, Math.max(0, next - Date.now()) + 50)
  }
}

function expire() {
  const now = Date.now()
  let changed = false
  for (const [key, entry] of state) {
    if (entry.until <= now) {
      state.delete(key)
      changed = true
    }
  }
  if (changed) {
    emit()
  }
  schedule()
}

export function setActivity(
  conversationId: string,
  userId: string,
  activity: "" | "typing" | "recording"
) {
  if (activity === "") {
    state.delete(conversationId)
  } else {
    state.set(conversationId, { userId, activity, until: Date.now() + TTL })
  }
  emit()
  schedule()
}

export function clearActivity(conversationId: string, userId: string) {
  if (state.get(conversationId)?.userId === userId) {
    state.delete(conversationId)
    emit()
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function useActivity(conversationId: string): Activity | null {
  return useSyncExternalStore(
    subscribe,
    () => state.get(conversationId) ?? null
  )
}
