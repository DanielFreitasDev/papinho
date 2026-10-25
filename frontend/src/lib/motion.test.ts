import { afterEach, describe, expect, it } from "vitest"

import { handleSkippedViewTransitions } from "./motion"

describe("view transition interrompida", () => {
  afterEach(() => {
    Reflect.deleteProperty(document, "startViewTransition")
  })

  it("trata o ready rejeitado e devolve a transição do navegador", async () => {
    // Como no Chromium e no WebKit: a transição pulada rejeita o ready com AbortError.
    const updates: unknown[] = []
    Object.defineProperty(document, "startViewTransition", {
      configurable: true,
      writable: true,
      value: (update: unknown) => {
        updates.push(update)
        return {
          ready: Promise.reject(
            new DOMException("Transition was skipped", "AbortError")
          ),
          updateCallbackDone: Promise.resolve(),
          finished: Promise.resolve(),
          skipTransition: () => undefined,
        }
      },
    })
    handleSkippedViewTransitions()

    const update = () => undefined
    const transition = document.startViewTransition(update)

    expect(updates).toEqual([update])
    await expect(transition.updateCallbackDone).resolves.toBeUndefined()
    // Sem o tratamento, a rejeição do ready escapa e o Vitest reprova a rodada ("Unhandled Rejection").
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
})
