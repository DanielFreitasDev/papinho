// @vitest-environment node
import { fileURLToPath } from "node:url"
import { ESLint } from "eslint"
import { describe, expect, it } from "vitest"

/**
 * UI-05: a configuração real do ESLint (eslint.config.js) recusa controles com aparência nativa fora de
 * src/components/ui/. Cada trecho proibido precisa gerar o erro; os usos permitidos, não.
 */
const eslint = new ESLint({
  cwd: fileURLToPath(new URL("..", import.meta.url)),
})

const RULES = new Set([
  "no-restricted-syntax",
  "no-alert",
  "no-restricted-imports",
])

async function problems(
  jsx: string,
  filePath = "src/features/exemplo/tela.tsx"
) {
  const code = `export function Tela() {\n  return (\n    <>\n      ${jsx}\n    </>\n  )\n}\n`
  const [result] = await eslint.lintText(code, { filePath })
  return result.messages
    .filter((message) => message.ruleId && RULES.has(message.ruleId))
    .map((message) => message.message)
}

const FORBIDDEN: Record<string, string> = {
  select: `<select aria-label={rotulo}><option value="a" /></select>`,
  checkbox: `<input type="checkbox" />`,
  radio: `<Input type="radio" />`,
  range: `<input type="range" />`,
  date: `<Input type="date" />`,
  time: `<input type="time" />`,
  "datetime-local": `<InputGroupInput type="datetime-local" />`,
  color: `<input type="color" />`,
  password: `<Input type="password" />`,
  "file visível": `<input type="file" />`,
  progress: `<progress value={1} />`,
  meter: `<meter value={1} />`,
  "audio com controls": `<audio controls src="a.ogg" />`,
  "video com controls": `<video controls src="a.webm" />`,
  details: `<details><summary>{rotulo}</summary></details>`,
  "title nativo": `<span title={dica}>{rotulo}</span>`,
  "type em expressão": `<input type={"checkbox"} />`,
  "type em spread": `<Input {...{ type: "password" }} />`,
  "title em componente": `<Button title={dica}>{rotulo}</Button>`,
  "form sem noValidate": `<form onSubmit={() => undefined} />`,
}

const ALLOWED: Record<string, string> = {
  "file oculto": `<input type="file" hidden />`,
  "file sr-only": `<input type="file" className="sr-only" />`,
  "iframe com title": `<iframe title={titulo} src="https://www.youtube-nocookie.com/embed/x" />`,
  "form com noValidate": `<form noValidate onSubmit={() => undefined} />`,
  "campos de texto": `<Input type="email" /><Input type="search" /><Input type="number" />`,
  "mídia sem controls": `<audio src="a.ogg" /><video src="a.webm" />`,
  "componentes do projeto": `<Checkbox /><Slider /><Progress value={1} /><DatePicker />`,
  "title como conteúdo": `<SettingsSection title={titulo}>{rotulo}</SettingsSection><ConfirmDialog title={titulo} />`,
  "type em expressão permitido": `<Input type={"email"} />`,
}

describe("regra de lint do UI-05", () => {
  it.each(Object.entries(FORBIDDEN))(
    "recusa %s fora de components/ui",
    async (_, jsx) => {
      const found = await problems(jsx)
      expect(found.length).toBeGreaterThan(0)
      expect(found.every((message) => message.startsWith("UI-05"))).toBe(true)
    }
  )

  it.each(Object.entries(ALLOWED))("aceita %s", async (_, jsx) => {
    expect(await problems(jsx)).toEqual([])
  })

  it("deixa os componentes do shadcn usarem o elemento nativo", async () => {
    const jsx = Object.values(FORBIDDEN).join("\n      ")
    expect(await problems(jsx, "src/components/ui/exemplo.tsx")).toEqual([])
  })

  it("recusa alert/confirm/prompt e o NativeSelect", async () => {
    const [result] = await eslint.lintText(
      `import { NativeSelect } from "@/components/ui/native-select"\n` +
        `export function ok() {\n  window.alert("a")\n  return confirm("b") ? NativeSelect : prompt("c")\n}\n`,
      { filePath: "src/features/exemplo/acoes.tsx" }
    )
    const rules = result.messages.map((message) => message.ruleId)
    expect(rules.filter((rule) => rule === "no-alert")).toHaveLength(3)
    expect(rules).toContain("no-restricted-imports")
  })
})

describe("regra de lint do MS-01 (sem HTML cru)", () => {
  it("recusa dangerouslySetInnerHTML, também em components/ui", async () => {
    for (const filePath of [
      "src/features/exemplo/tela.tsx",
      "src/components/ui/exemplo.tsx",
    ]) {
      const found = await problems(
        `<span dangerouslySetInnerHTML={{ __html: texto }} />`,
        filePath
      )
      expect(found).toHaveLength(1)
      expect(found[0]).toMatch(/^MS-01/)
    }
  })

  it("recusa innerHTML, outerHTML e insertAdjacentHTML", async () => {
    const [result] = await eslint.lintText(
      `export function f(el: HTMLElement, texto: string) {\n` +
        `  el.innerHTML = texto\n  el.outerHTML = texto\n` +
        `  el.insertAdjacentHTML("beforeend", texto)\n}\n`,
      { filePath: "src/lib/exemplo.ts" }
    )
    expect(
      result.messages
        .filter((message) => message.ruleId === "no-restricted-syntax")
        .map((message) => message.message.slice(0, 5))
    ).toEqual(["MS-01", "MS-01", "MS-01"])
  })

  it("aceita textContent, sem HTML cru", async () => {
    const [result] = await eslint.lintText(
      `export function f(el: HTMLElement, texto: string) {\n  el.textContent = texto\n}\n`,
      { filePath: "src/lib/exemplo.ts" }
    )
    expect(result.messages).toEqual([])
  })
})

describe("regra de lint do UI-03 (ação com falha visível)", () => {
  const lint = async (body: string) => {
    const [result] = await eslint.lintText(
      `export function Tela({ star }: { star: { mutate: (...args: unknown[]) => void } }) {\n` +
        `  const onError = () => undefined\n  void onError\n${body}\n  return null\n}\n`,
      { filePath: "src/features/exemplo/acao.tsx" }
    )
    return result.messages
      .filter((message) => message.ruleId === "no-restricted-syntax")
      .map((message) => message.message.slice(0, 5))
  }

  it("recusa .mutate sem onError", async () => {
    expect(await lint(`  star.mutate({ id: 1 })`)).toEqual(["UI-03"])
    expect(
      await lint(`  star.mutate({ id: 1 }, { onSuccess: () => undefined })`)
    ).toEqual(["UI-03"])
  })

  it("aceita .mutate com onError", async () => {
    expect(await lint(`  star.mutate({ id: 1 }, { onError })`)).toEqual([])
    expect(
      await lint(`  star.mutate(undefined, { onError: () => undefined })`)
    ).toEqual([])
  })
})

describe("regra de lint do IN-01 (texto só pelo i18next)", () => {
  const LITERAL: Record<string, string> = {
    "texto no JSX": `<p>Olá, tudo bem?</p>`,
    "texto numa expressão": `<p>{"Enviar"}</p>`,
    "template no JSX": "<p>{`Olá ${nome}`}</p>",
    "aria-label fixo": `<button aria-label="Fechar" />`,
    "placeholder fixo": `<Input placeholder="Buscar" />`,
    "alt fixo": `<img alt="Foto" src="a.png" />`,
    "aria-label em template": "<button aria-label={`Abrir ${nome}`} />",
  }

  it.each(Object.entries(LITERAL))("recusa %s", async (_, jsx) => {
    const found = await problems(jsx)
    expect(found.length).toBeGreaterThan(0)
    expect(found.every((message) => message.startsWith("IN-01"))).toBe(true)
  })

  it("aceita chaves, números e símbolos", async () => {
    expect(
      await problems(
        '<p>{t("chat.title")} · {count} – 3/4 @{usuario} {`${a} · ${b}`}</p><button aria-label={t("chat.send")} />'
      )
    ).toEqual([])
  })

  it("deixa os componentes do shadcn com o texto do registry", async () => {
    expect(
      await problems(
        `<span className="sr-only">Close</span>`,
        "src/components/ui/exemplo.tsx"
      )
    ).toEqual([])
  })
})
