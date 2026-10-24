import { afterEach, describe, expect, it, vi } from "vitest"

import { player, type Track } from "./player"

const track: Track = {
  mediaId: "audio-1",
  conversationId: "conversa",
  messageId: "mensagem",
  title: "Ana · Áudio",
  durationMs: 2280,
}

/** O jsdom não toca mídia: guarda o elemento do tocador global para disparar os eventos à mão. */
function stubMedia() {
  const played: HTMLMediaElement[] = []
  vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(function (
    this: HTMLMediaElement
  ) {
    played.push(this)
    return Promise.resolve()
  })
  vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {})
  vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => {})
  return played
}

function reportDuration(element: HTMLMediaElement, seconds: number) {
  Object.defineProperty(element, "duration", {
    configurable: true,
    value: seconds,
  })
  element.dispatchEvent(new Event("durationchange"))
}

describe("tocador global", () => {
  afterEach(() => {
    player.stop()
    vi.restoreAllMocks()
  })

  it("usa a duração lida pelo servidor desde o play (AU-02)", () => {
    stubMedia()
    player.play(track)
    expect(player.snapshot().duration).toBe(2.28)
  })

  it("WebM sem duração (Infinity) mantém a do servidor; duração real do navegador prevalece", () => {
    const played = stubMedia()
    player.play(track)
    const element = played[0]
    reportDuration(element, Infinity)
    expect(player.snapshot().duration).toBe(2.28)
    reportDuration(element, 2.3)
    expect(player.snapshot().duration).toBe(2.3)
  })

  it("mensagem antiga sem duração fica com 0 até o navegador saber", () => {
    const played = stubMedia()
    player.play({ ...track, mediaId: "audio-2", durationMs: null })
    expect(player.snapshot().duration).toBe(0)
    reportDuration(played[0], Infinity)
    expect(player.snapshot().duration).toBe(0)
  })
})
