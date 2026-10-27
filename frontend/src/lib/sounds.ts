/**
 * Som curto ao enviar uma mensagem ("Reproduzir sons ao enviar mensagens" do WhatsApp), feito na hora com a Web Audio
 * API: um "plim" que sobe de tom em 80 ms. Nada para baixar nem para a CSP liberar. O contexto de áudio nasce no
 * primeiro envio, que é um gesto da pessoa (o navegador só toca depois de um).
 */
let context: AudioContext | null = null

export function playSendSound() {
  try {
    context ??= new AudioContext()
    const now = context.currentTime
    const tone = context.createOscillator()
    const volume = context.createGain()
    tone.type = "sine"
    tone.frequency.setValueAtTime(560, now)
    tone.frequency.exponentialRampToValueAtTime(1120, now + 0.08)
    volume.gain.setValueAtTime(0.0001, now)
    volume.gain.exponentialRampToValueAtTime(0.16, now + 0.012)
    volume.gain.exponentialRampToValueAtTime(0.0001, now + 0.16)
    tone.connect(volume).connect(context.destination)
    tone.start(now)
    tone.stop(now + 0.17)
  } catch {
    // Sem áudio no navegador: o envio segue em silêncio.
  }
}
