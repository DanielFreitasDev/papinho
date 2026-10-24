/**
 * Atividade da outra pessoa ("digitando…", "gravando áudio…", MS-03): as reticências viram três pontos que pulam em
 * sequência. Para leitor de tela e busca de texto, continua o mesmo texto (as reticências ficam escondidas na tela).
 */
export function ActivityText({ text }: { text: string }) {
  if (!text.endsWith("…")) {
    return text
  }
  return (
    <>
      {text.slice(0, -1)}
      <span className="sr-only">…</span>
      <span
        aria-hidden="true"
        className="ms-0.5 inline-flex items-end gap-0.5 pb-[0.2em]"
      >
        <span className="inline-block size-1 animate-typing-dot rounded-full bg-current" />
        <span className="inline-block size-1 animate-typing-dot-2 rounded-full bg-current" />
        <span className="inline-block size-1 animate-typing-dot-3 rounded-full bg-current" />
      </span>
    </>
  )
}
