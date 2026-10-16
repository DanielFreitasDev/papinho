import { Client, type IMessage } from "@stomp/stompjs"

import { readCookie } from "@/lib/api-client"

/**
 * Conexão STOMP com o servidor (ADR 0004). O WebSocket só avisa que algo mudou; o conteúdo vem pela API. Na
 * reconexão quem escuta sincroniza de novo (afterSeq, ADR 0010).
 */
export type RealtimeEvent =
  | { type: "messages"; conversationId: string; lastSeq: number }
  | {
      type: "receipts"
      conversationId: string
      userId: string
      deliveredSeq: number
      readSeq: number
    }
  | { type: "conversation"; conversationId: string }
  | { type: "hidden"; conversationId: string; messageIds: string[] }
  | { type: "cleared"; conversationId: string }
  | { type: "folders" }
  | {
      type: "activity"
      conversationId: string
      userId: string
      activity: "" | "typing" | "recording"
    }
  | {
      type: "presence"
      userId: string
      online: boolean
      lastSeenAt: string | null
    }

export type ConnectionState = "connecting" | "connected" | "offline" | "ended"

type Listener = (event: RealtimeEvent) => void
type StateListener = (state: ConnectionState, reconnected: boolean) => void

/** Código de fechamento do servidor quando a sessão HTTP acabou (RealtimeSessions.SESSION_ENDED). */
const SESSION_ENDED = 4401
/** Heartbeat de presença; o servidor considera a conexão parada depois de 45 s. */
const PRESENCE_INTERVAL = 20_000

class Realtime {
  private client: Client | null = null
  private readonly listeners = new Set<Listener>()
  private readonly stateListeners = new Set<StateListener>()
  private state: ConnectionState = "offline"
  private connectedOnce = false
  private presenceTimer: number | undefined

  start() {
    if (this.client) {
      return
    }
    const protocol = location.protocol === "https:" ? "wss" : "ws"
    const client = new Client({
      brokerURL: `${protocol}://${location.host}/ws`,
      heartbeatIncoming: 20_000,
      heartbeatOutgoing: 20_000,
      reconnectDelay: 2_000,
      beforeConnect: async () => {
        if (!readCookie("XSRF-TOKEN")) {
          await fetch("/api/v1/instance", { credentials: "same-origin" })
        }
        client.connectHeaders = {
          "X-XSRF-TOKEN": decodeURIComponent(readCookie("XSRF-TOKEN") ?? ""),
        }
        this.setState("connecting")
      },
      onConnect: () => {
        client.subscribe("/user/queue/events", (frame) => this.dispatch(frame))
        const reconnected = this.connectedOnce
        this.connectedOnce = true
        this.setState("connected", reconnected)
        this.sendPresence()
      },
      onWebSocketClose: (event: CloseEvent) => {
        if (event.code === SESSION_ENDED) {
          this.stop()
          this.setState("ended")
          return
        }
        if (this.client) {
          this.setState("offline")
        }
      },
    })
    this.client = client
    client.activate()
    document.addEventListener("visibilitychange", this.sendPresence)
    window.addEventListener("focus", this.sendPresence)
    window.addEventListener("blur", this.sendPresence)
    this.presenceTimer = window.setInterval(
      this.sendPresence,
      PRESENCE_INTERVAL
    )
  }

  stop() {
    const client = this.client
    this.client = null
    this.connectedOnce = false
    window.clearInterval(this.presenceTimer)
    document.removeEventListener("visibilitychange", this.sendPresence)
    window.removeEventListener("focus", this.sendPresence)
    window.removeEventListener("blur", this.sendPresence)
    void client?.deactivate()
    this.setState("offline")
  }

  subscribe(listener: Listener) {
    this.listeners.add(listener)
    return () => {
      this.listeners.delete(listener)
    }
  }

  onState(listener: StateListener) {
    this.stateListeners.add(listener)
    return () => {
      this.stateListeners.delete(listener)
    }
  }

  currentState() {
    return this.state
  }

  /** "digitando…" / "gravando áudio…" (MS-03); vazio para parar. */
  activity(conversationId: string, activity: "" | "typing" | "recording") {
    this.publish("/app/activity", { conversationId, activity })
  }

  private publish(destination: string, body: unknown) {
    if (this.client?.connected) {
      this.client.publish({ destination, body: JSON.stringify(body) })
    }
  }

  /** Aba visível conta como online; em segundo plano, não (RT-03). */
  private readonly sendPresence = () => {
    this.publish("/app/presence", {
      active: document.visibilityState === "visible",
    })
  }

  private dispatch(frame: IMessage) {
    let event: RealtimeEvent
    try {
      event = JSON.parse(frame.body) as RealtimeEvent
    } catch {
      return
    }
    this.listeners.forEach((listener) => listener(event))
  }

  private setState(state: ConnectionState, reconnected = false) {
    if (this.state === state && !reconnected) {
      return
    }
    this.state = state
    this.stateListeners.forEach((listener) => listener(state, reconnected))
  }
}

export const realtime = new Realtime()
