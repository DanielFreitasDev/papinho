import { readFileSync } from "node:fs"
import { describe, expect, it } from "vitest"

function manifest(path: string) {
  return JSON.parse(readFileSync(new URL(path, import.meta.url), "utf8")) as {
    dependencies: Record<string, string>
  }
}

/**
 * ADR 0008: o worker importa os arquivos do onnxruntime-web direto do pacote; a versão precisa ser a mesma que o
 * Transformers.js usa, senão a API e o .wasm deixam de casar (achado A92).
 */
describe("onnxruntime-web", () => {
  it("é a mesma versão que o Transformers.js pede", () => {
    const app = manifest("../../../package.json")
    const transformers = manifest(
      "../../../node_modules/@huggingface/transformers/package.json"
    )
    expect(app.dependencies["onnxruntime-web"]).toBe(
      transformers.dependencies["onnxruntime-web"]
    )
  })
})
