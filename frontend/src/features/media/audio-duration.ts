/** Acima disso não vale decodificar o arquivo inteiro na memória só para saber a duração. */
const DECODE_LIMIT = 25 * 1024 * 1024
/** Metadados que não chegam nesse prazo não chegam mais (arquivo que o navegador não toca). */
const METADATA_TIMEOUT = 5_000

/**
 * Duração em milissegundos de um áudio ainda no aparelho (gravação ou arquivo escolhido), para a bolha mostrar
 * enquanto envia (AU-02); depois vale a que o servidor lê do arquivo. Os metadados do elemento bastam para MP3,
 * M4A, Ogg e WAV; o WebM do MediaRecorder chega sem duração (Infinity) e é decodificado, o que dá o mesmo valor
 * que o servidor calcula. Nulo quando o navegador não consegue dizer.
 */
export async function measureAudioDuration(file: Blob): Promise<number | null> {
  const fromMetadata = await metadataDuration(file)
  if (fromMetadata !== null || file.size > DECODE_LIMIT) {
    return fromMetadata
  }
  try {
    // OfflineAudioContext não depende de gesto do usuário; a taxa baixa só barateia a decodificação.
    const context = new OfflineAudioContext(1, 1, 8_000)
    const buffer = await context.decodeAudioData(await file.arrayBuffer())
    return Math.round(buffer.duration * 1000)
  } catch {
    return null
  }
}

function metadataDuration(file: Blob): Promise<number | null> {
  return new Promise((resolve) => {
    const audio = document.createElement("audio")
    const url = URL.createObjectURL(file)
    const finish = (value: number | null) => {
      window.clearTimeout(timer)
      audio.onloadedmetadata = null
      audio.onerror = null
      audio.removeAttribute("src")
      URL.revokeObjectURL(url)
      resolve(value)
    }
    const timer = window.setTimeout(() => finish(null), METADATA_TIMEOUT)
    audio.preload = "metadata"
    audio.onloadedmetadata = () =>
      finish(
        Number.isFinite(audio.duration) && audio.duration > 0
          ? Math.round(audio.duration * 1000)
          : null
      )
    audio.onerror = () => finish(null)
    audio.src = url
  })
}
