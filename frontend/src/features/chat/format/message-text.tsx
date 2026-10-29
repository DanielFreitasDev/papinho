import {
  createContext,
  Fragment,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react"
import { Link } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"
import { cn } from "cn"

import type { HastNode } from "./highlight"
import { parseMessage, type Block, type Inline } from "./parse"
import { highlightText } from "./search-highlight"
import type { SearchTerms } from "./search-match"

/**
 * Nome de cada membro do grupo pelo @usuario: a menção aparece na bolha com o nome, como no WhatsApp (MS-04); o texto
 * guardado continua `@usuario`. Fora de um grupo o mapa fica vazio e a menção mostra o @usuario.
 */
export const MentionNames = createContext<ReadonlyMap<string, string>>(
  new Map()
)

function Mention({
  username,
  highlight,
}: {
  username: string
  highlight: SearchTerms | null | undefined
}) {
  const name = useContext(MentionNames).get(username.toLowerCase())
  return (
    <Link
      to="/add/$username"
      params={{ username }}
      className={cn(
        "font-medium underline-offset-2 hover:underline",
        name !== undefined && "text-primary"
      )}
      data-private={name !== undefined ? "names" : undefined}
    >
      {highlightText(`@${name ?? username}`, highlight)}
    </Link>
  )
}

/**
 * Corpo de uma mensagem com a formatação do WhatsApp, sem HTML do usuário (MS-01). Com uma busca aberta, os trechos
 * achados ganham o marca-texto (GR-06).
 */
export function MessageText({
  text,
  highlight,
}: {
  text: string
  highlight?: SearchTerms | null
}) {
  return (
    <div className="flex flex-col gap-1.5">
      {renderBlocks(parseMessage(text), highlight)}
    </div>
  )
}

function renderBlocks(
  blocks: Block[],
  highlight: SearchTerms | null | undefined
): ReactNode[] {
  return blocks.map((block, index) => {
    switch (block.type) {
      case "paragraph":
        return (
          <p key={index} className="whitespace-pre-wrap">
            {renderInline(block.children, highlight)}
          </p>
        )
      case "code":
        return (
          <CodeBlock
            key={index}
            language={block.language}
            value={block.value}
            highlight={highlight}
          />
        )
      case "quote":
        return (
          <blockquote
            key={index}
            className="border-l-4 border-current/30 pl-2 opacity-90"
          >
            {renderBlocks(block.children, highlight)}
          </blockquote>
        )
      case "list": {
        const items = block.items.map((item, position) => (
          <li key={position}>{renderInline(item, highlight)}</li>
        ))
        return block.ordered ? (
          <ol key={index} start={block.start} className="list-decimal pl-5">
            {items}
          </ol>
        ) : (
          <ul key={index} className="list-disc pl-5">
            {items}
          </ul>
        )
      }
    }
  })
}

function renderInline(
  nodes: Inline[],
  highlight: SearchTerms | null | undefined
): ReactNode[] {
  return nodes.map((node, index) => {
    switch (node.type) {
      case "text":
        return (
          <Fragment key={index}>
            {highlightText(node.value, highlight)}
          </Fragment>
        )
      case "break":
        return <br key={index} />
      case "code":
        return (
          <code
            key={index}
            className="rounded bg-black/10 px-1 font-mono text-[0.9em] dark:bg-white/15"
          >
            {highlightText(node.value, highlight)}
          </code>
        )
      case "mention":
        // @todos menciona todo o grupo (MS-04): destaque, sem link de perfil.
        if (node.username === "todos") {
          return (
            <strong key={index} className="font-semibold text-primary">
              {/* eslint-disable-next-line no-restricted-syntax -- é o próprio texto digitado (@todos), não rótulo */}
              {"@todos"}
            </strong>
          )
        }
        return (
          <Mention key={index} username={node.username} highlight={highlight} />
        )
      case "link":
        return (
          <a
            key={index}
            href={node.href}
            target="_blank"
            rel="noopener noreferrer nofollow"
            className="break-all underline underline-offset-2"
          >
            {highlightText(node.href, highlight)}
          </a>
        )
      case "bold":
        return (
          <strong key={index}>{renderInline(node.children, highlight)}</strong>
        )
      case "italic":
        return <em key={index}>{renderInline(node.children, highlight)}</em>
      case "strike":
        return <s key={index}>{renderInline(node.children, highlight)}</s>
      case "spoiler":
        // Sem marca-texto no spoiler: o fundo amarelo mostraria onde está o termo antes de revelar.
        return (
          <Spoiler key={index}>{renderInline(node.children, null)}</Spoiler>
        )
    }
  })
}

/** ||spoiler||: escondido até tocar; enquanto escondido, leitor de tela ouve só o aviso. */
function Spoiler({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const [revealed, setRevealed] = useState(false)
  if (revealed) {
    return <span className="rounded bg-current/10 px-0.5">{children}</span>
  }
  return (
    <button
      type="button"
      onClick={() => setRevealed(true)}
      className="inline rounded bg-current/80 px-0.5 align-baseline outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <span aria-hidden="true" className="opacity-0 select-none">
        {children}
      </span>
      <span className="sr-only">{t("chat.spoiler")}</span>
    </button>
  )
}

function CodeBlock({
  language,
  value,
  highlight,
}: {
  language: string | null
  value: string
  highlight: SearchTerms | null | undefined
}) {
  const [tree, setTree] = useState<HastNode[] | null>(null)
  useEffect(() => {
    if (!language) {
      return
    }
    let cancelled = false
    void import("./highlight").then(({ highlight }) => {
      if (!cancelled) {
        setTree(highlight(language, value))
      }
    })
    return () => {
      cancelled = true
    }
  }, [language, value])
  return (
    <pre
      className="max-w-full overflow-x-auto rounded-md bg-black/10 p-2 font-mono text-[0.85em] dark:bg-white/10"
      data-language={language ?? undefined}
    >
      <code className="hljs">
        {tree ? renderHast(tree, highlight) : highlightText(value, highlight)}
      </code>
    </pre>
  )
}

function renderHast(
  nodes: HastNode[],
  highlight: SearchTerms | null | undefined
): ReactNode[] {
  return nodes.map((node, index) =>
    node.type === "text" ? (
      <Fragment key={index}>{highlightText(node.value, highlight)}</Fragment>
    ) : (
      <span key={index} className={cn(node.properties?.className)}>
        {renderHast(node.children, highlight)}
      </span>
    )
  )
}
