// @vitest-environment node
import { readdirSync, readFileSync } from "node:fs"
import { join, relative } from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"

const SRC = fileURLToPath(new URL("../src", import.meta.url))
const locale = (name: string): unknown =>
  JSON.parse(readFileSync(join(SRC, "locales", `${name}.json`), "utf8"))
const ptBR = locale("pt-BR")
const en = locale("en")

function sources(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) {
      return sources(path)
    }
    return /\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)
      ? [path]
      : []
  })
}

function lookup(tree: unknown, key: string): unknown {
  return key
    .split(".")
    .reduce<unknown>(
      (node, part) =>
        typeof node === "object" && node !== null
          ? (node as Record<string, unknown>)[part]
          : undefined,
      tree
    )
}

/** A chave existe como texto, ou como plural do i18next (chave_one, chave_other). */
function has(tree: unknown, key: string) {
  return [key, `${key}_one`, `${key}_other`].some(
    (candidate) => typeof lookup(tree, candidate) === "string"
  )
}

/**
 * Chaves literais do código: t("a.b"), i18n.t("a.b"), i18nKey="a.b" e listas de seções com key: "a.b". Chaves
 * montadas em tempo de execução (t(`states.${x}`)) ficam de fora.
 */
const KEY_USE =
  /(?:\bt|\bi18n\.t)\(\s*["']([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)["']|i18nKey=["']([A-Za-z0-9_.]+)["']|\bkey:\s*["']([a-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)["']/g

function usedKeys() {
  const used = new Map<string, string>()
  for (const file of sources(SRC)) {
    const text = readFileSync(file, "utf8")
    for (const match of text.matchAll(KEY_USE)) {
      const key = match[1] ?? match[2] ?? match[3]
      if (!used.has(key)) {
        used.set(key, relative(SRC, file))
      }
    }
  }
  return used
}

/** IN-01: toda chave usada no código existe nos dois idiomas (a paridade fica em i18n.test.ts). */
describe("chaves usadas no código", () => {
  const used = usedKeys()

  it("encontra as chaves do código", () => {
    // Guarda contra a expressão regular parar de achar chaves (o teste passaria vazio).
    expect(used.size).toBeGreaterThan(500)
    expect(used.get("common.genericError")).toBeDefined()
  })

  it.each([
    ["pt-BR", ptBR],
    ["en", en],
  ])("existem em %s", (_, tree) => {
    const missing = [...used]
      .filter(([key]) => !has(tree, key))
      .map(([key, file]) => `${key} (${file})`)
    expect(missing).toEqual([])
  })
})
