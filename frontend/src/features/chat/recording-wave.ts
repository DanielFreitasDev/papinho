/** A onda da gravação ganha uma barra a cada BAR_MS com o pico do trecho; as barras correm para a esquerda. */
export const BAR_MS = 90
export const BAR_WIDTH = 3
export const BAR_STEP = 5
const MIN_BAR = 2
/** Faixa de volume mostrada: abaixo do piso é silêncio (ruído do microfone), acima do teto é barra cheia. */
const FLOOR_DB = -50
const CEIL_DB = -10

/**
 * Nível de 0 a 1 de um trecho do microfone (amostras do getByteTimeDomainData, 128 é o silêncio), em decibéis: a voz
 * ocupa a altura toda sem que o ruído de fundo pareça fala.
 */
export function levelFromSamples(samples: Uint8Array): number {
  if (samples.length === 0) {
    return 0
  }
  let sum = 0
  for (const sample of samples) {
    const centered = (sample - 128) / 128
    sum += centered * centered
  }
  const rms = Math.sqrt(sum / samples.length)
  if (rms === 0) {
    return 0
  }
  const db = 20 * Math.log10(rms)
  return Math.min(1, Math.max(0, (db - FLOOR_DB) / (CEIL_DB - FLOOR_DB)))
}

export type WaveBar = { x: number; y: number; width: number; height: number }

/**
 * Barras da onda em px: levels[0] é a mais nova, na borda direita; progress (0 a 1) é quanto do passo já correu até a
 * próxima barra, para todas deslizarem juntas. As que saíram pela esquerda ficam de fora.
 */
export function waveformBars(
  levels: readonly number[],
  width: number,
  height: number,
  progress: number
): WaveBar[] {
  const bars: WaveBar[] = []
  for (let index = 0; index < levels.length; index++) {
    const x = width - BAR_WIDTH - (index + progress) * BAR_STEP
    if (x + BAR_WIDTH <= 0) {
      break
    }
    const barHeight = Math.min(
      height,
      Math.max(MIN_BAR, levels[index] * height)
    )
    bars.push({
      x,
      y: (height - barHeight) / 2,
      width: BAR_WIDTH,
      height: barHeight,
    })
  }
  return bars
}

/** Quantas barras cabem numa onda desta largura (o que passar disso pode ser descartado). */
export function barsFor(width: number): number {
  return Math.ceil(width / BAR_STEP) + 2
}
