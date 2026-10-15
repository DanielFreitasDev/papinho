import { common, createLowlight } from "lowlight"

/**
 * Destaque de sintaxe dos blocos de código (MS-01). Carregado sob demanda: só baixa quando a conversa tem um
 * bloco com linguagem. Devolve a árvore hast do lowlight, que o render transforma em elementos React.
 */
const lowlight = createLowlight(common)

export type HastNode =
  | { type: "text"; value: string }
  | {
      type: "element"
      tagName: string
      properties?: { className?: string[] }
      children: HastNode[]
    }

export function highlight(language: string, code: string): HastNode[] | null {
  const name = language.toLowerCase()
  if (!lowlight.registered(name)) {
    return null
  }
  return lowlight.highlight(name, code).children as HastNode[]
}
