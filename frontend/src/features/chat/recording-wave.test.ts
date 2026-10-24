import { describe, expect, it } from "vitest"

import {
  BAR_STEP,
  BAR_WIDTH,
  barsFor,
  levelFromSamples,
  waveformBars,
} from "./recording-wave"

function tone(amplitude: number, length = 512) {
  return Uint8Array.from({ length }, (_, index) =>
    Math.round(
      128 + amplitude * 127 * Math.sin((index / length) * 16 * Math.PI)
    )
  )
}

describe("levelFromSamples", () => {
  it("dá zero para o silêncio e para o ruído abaixo do piso", () => {
    expect(levelFromSamples(new Uint8Array(512).fill(128))).toBe(0)
    // Um degrau de 8 bits a cada 64 amostras: cerca de -60 dB, ruído do microfone.
    const noise = new Uint8Array(512)
      .fill(128)
      .map((sample, index) => (index % 64 === 0 ? 129 : sample))
    expect(levelFromSamples(noise)).toBe(0)
    expect(levelFromSamples(new Uint8Array())).toBe(0)
  })

  it("sobe com o volume e satura na barra cheia", () => {
    const soft = levelFromSamples(tone(0.02))
    const loud = levelFromSamples(tone(0.2))
    expect(soft).toBeGreaterThan(0)
    expect(loud).toBeGreaterThan(soft)
    expect(levelFromSamples(tone(1))).toBe(1)
  })
})

describe("waveformBars", () => {
  it("põe a barra mais nova na borda direita e as antigas à esquerda", () => {
    const bars = waveformBars([1, 0.5, 0], 100, 32, 0)
    expect(bars.map((bar) => bar.x)).toEqual([
      100 - BAR_WIDTH,
      100 - BAR_WIDTH - BAR_STEP,
      100 - BAR_WIDTH - 2 * BAR_STEP,
    ])
    expect(bars[0]).toMatchObject({ y: 0, height: 32 })
    expect(bars[1]).toMatchObject({ y: 8, height: 16 })
    // O silêncio ainda aparece como um ponto, centrado.
    expect(bars[2]).toMatchObject({ y: 15, height: 2 })
  })

  it("desliza todas juntas e descarta as que saíram pela esquerda", () => {
    const halfway = waveformBars([1, 1], 100, 32, 0.5)
    expect(halfway[0].x).toBe(100 - BAR_WIDTH - BAR_STEP / 2)
    const full = waveformBars(Array(100).fill(0.3), 20, 32, 0)
    expect(full.length).toBe(Math.ceil(20 / BAR_STEP))
    expect(full.every((bar) => bar.x + bar.width > 0)).toBe(true)
    expect(barsFor(20)).toBeGreaterThanOrEqual(full.length)
  })
})
