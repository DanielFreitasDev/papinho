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
  select: `<select aria-label="x"><option>a</option></select>`,
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
  details: `<details><summary>a</summary></details>`,
  "title nativo": `<span title="dica">a</span>`,
  "form sem noValidate": `<form onSubmit={() => undefined} />`,
}

const ALLOWED: Record<string, string> = {
  "file oculto": `<input type="file" hidden />`,
  "file sr-only": `<input type="file" className="sr-only" />`,
  "iframe com title": `<iframe title="Vídeo" src="https://www.youtube-nocookie.com/embed/x" />`,
  "form com noValidate": `<form noValidate onSubmit={() => undefined} />`,
  "campos de texto": `<Input type="email" /><Input type="search" /><Input type="number" />`,
  "mídia sem controls": `<audio src="a.ogg" /><video src="a.webm" />`,
  "componentes do projeto": `<Checkbox /><Slider /><Progress value={1} /><DatePicker />`,
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
