import { describe, expect, it } from "vitest"
import { render, screen } from "@testing-library/react"

import { AudioControls } from "./audio-controls"

function renderControls(
  props: Partial<Parameters<typeof AudioControls>[0]> = {}
) {
  return render(
    <AudioControls
      playing={false}
      position={0}
      duration={0}
      onToggle={() => undefined}
      onSeek={() => undefined}
      {...props}
    />
  )
}

/** O tempo é o último texto dos controles (AU-02). */
function shownTime(container: HTMLElement) {
  return container.querySelector(".tabular-nums")?.textContent
}

describe("AudioControls", () => {
  it("mostra a duração total antes do primeiro play", () => {
    const { container } = renderControls({ duration: 42.7 })
    expect(shownTime(container)).toBe("Duração 0:42")
  })

  it("mostra o tempo decorrido enquanto toca", () => {
    const { container } = renderControls({
      playing: true,
      position: 5.4,
      duration: 42.7,
    })
    expect(shownTime(container)).toBe("Tempo decorrido 0:05")
  })

  it("volta para o total ao pausar ou terminar", () => {
    const { container } = renderControls({ position: 5.4, duration: 42.7 })
    expect(shownTime(container)).toBe("Duração 0:42")
  })

  it("áudio de menos de um segundo não aparece como 0:00", () => {
    const { container } = renderControls({ duration: 0.4 })
    expect(shownTime(container)).toBe("Duração 0:01")
  })

  it("sem a duração (mensagem antiga), mostra a posição como antes", () => {
    const { container } = renderControls({ position: 3 })
    expect(shownTime(container)).toBe("Tempo decorrido 0:03")
  })

  it("enquanto sobe, nada toca e o tempo só aparece depois de medido", () => {
    const { container, rerender } = renderControls({ unavailable: true })
    expect(screen.getByRole("button", { name: "Tocar áudio" })).toBeDisabled()
    expect(shownTime(container)).toBe("\u00a0")
    rerender(
      <AudioControls
        playing={false}
        position={0}
        duration={2.28}
        unavailable
        onToggle={() => undefined}
        onSeek={() => undefined}
      />
    )
    expect(shownTime(container)).toBe("Duração 0:02")
  })
})
