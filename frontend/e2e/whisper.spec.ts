import { fileURLToPath } from "node:url"

import { expect, test } from "./fixtures"
import { chooseAttachment, RUN, twoFriends } from "./support"

/**
 * Whisper no navegador de verdade (AU-04): baixa o modelo do Hugging Face com progresso, transcreve no Web
 * Worker um áudio com fala e guarda o texto no servidor para os outros membros (AU-03). Precisa de internet e
 * baixa ~80 MB, então só roda com PAPINHO_E2E_WHISPER=1; PAPINHO_E2E_PROXY indica o proxy quando a internet
 * passa por um (a instância local continua direta).
 *
 * O áudio é o trecho do discurso de posse de John F. Kennedy (1961, domínio público) usado nos exemplos do
 * Transformers.js, convertido para Ogg Opus mono.
 */
const proxy = process.env.PAPINHO_E2E_PROXY
if (proxy) {
  test.use({ proxy: { server: proxy, bypass: "localhost,127.0.0.1" } })
}

test("transcreve no navegador com o modelo baixado do Hugging Face", async ({
  browser,
  pageErrors,
}) => {
  test.skip(
    !process.env.PAPINHO_E2E_WHISPER,
    "precisa de internet: PAPINHO_E2E_WHISPER=1"
  )
  const project = test.info().project.name
  test.skip(
    project !== "desktop-chromium" && project !== "whisper-webgpu",
    "WASM no desktop-chromium e WebGPU no whisper-webgpu bastam"
  )
  test.setTimeout(10 * 60_000)
  const { gil, hana } = await twoFriends(
    browser,
    pageErrors,
    `${RUN}w`,
    "Whisper"
  )

  const chooser = gil.page.waitForEvent("filechooser")
  await chooseAttachment(gil.page, "Áudio")
  await (
    await chooser
  ).setFiles(fileURLToPath(new URL("./assets/fala.ogg", import.meta.url)))
  const dialog = gil.page.getByRole("dialog", { name: /Enviar 1 arquivo/ })
  await dialog.getByRole("button", { name: "Enviar" }).click()
  await expect(dialog).toBeHidden()

  // Hana escolhe transcrever no próprio navegador, seja qual for o padrão da instância.
  await hana.page.goto("/settings/transcription")
  await hana.page.getByRole("combobox", { name: "Onde transcrever" }).click()
  await hana.page
    .getByRole("option", { name: "No navegador (Whisper)", exact: true })
    .click()
  await expect(
    hana.page.getByRole("combobox", { name: "Onde transcrever" })
  ).toContainText("No navegador (Whisper)")
  await hana.page.goto("/")
  // O worker escolhe WebGPU quando o navegador oferece um adaptador; senão, WASM.
  const webgpu = await hana.page.evaluate(
    async () =>
      "gpu" in navigator &&
      Boolean(
        await (
          navigator as Navigator & {
            gpu: { requestAdapter(): Promise<unknown> }
          }
        ).gpu.requestAdapter()
      )
  )
  expect(webgpu).toBe(project === "whisper-webgpu")
  // Os arquivos do modelo mostram o caminho usado: fp32/q4 no WebGPU, quantizados (q8) no WASM.
  const models = new Set<string>()
  hana.context.on("request", (request) => {
    const file = /\/onnx\/([\w.-]+\.onnx)/.exec(request.url())?.[1]
    if (file) {
      models.add(file)
    }
  })
  const conversation = hana.page
    .getByRole("complementary", { name: "Conversas e contatos" })
    .getByRole("link", { name: new RegExp(gil.name) })
  await expect(conversation).toBeVisible()
  await conversation.click()

  const transcribe = hana.page.getByRole("button", { name: "Transcrever" })
  await expect(transcribe).toBeVisible()
  await transcribe.click()
  await expect(
    hana.page.getByRole("status").filter({ hasText: /Baixando o modelo/ })
  ).toBeVisible({ timeout: 60_000 })
  const transcript = hana.page.getByText(/ask not what your country/i)
  await expect(transcript).toBeVisible({ timeout: 8 * 60_000 })
  console.log(`transcrição: ${await transcript.textContent()}`)
  expect([...models].sort()).toEqual(
    webgpu
      ? ["decoder_model_merged_q4.onnx", "encoder_model.onnx"]
      : ["decoder_model_merged_quantized.onnx", "encoder_model_quantized.onnx"]
  )

  // O texto fica no servidor: quem enviou vê sem transcrever de novo.
  await gil.page.reload()
  await expect(gil.page.getByText(/ask not what your country/i)).toBeVisible()

  await Promise.all([gil.context.close(), hana.context.close()])
})
