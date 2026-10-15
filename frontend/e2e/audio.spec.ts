import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { openMenuItem, RUN, twoFriends } from "./support"

/**
 * Fase 3: mensagem de voz gravada com o microfone falso do Chromium (AU-01, MS-03) e tocador com velocidade,
 * sequência e mini-player global (AU-02).
 */
test("grava, pré-escuta e envia voz; o outro lado toca em sequência e no mini-player", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}a`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Voz")

  // Primeira gravação: parar e ouvir antes de enviar; o outro lado vê "gravando áudio…".
  await gil.page.getByRole("button", { name: "Gravar mensagem de voz" }).click()
  const recorder = gil.page.getByRole("group", {
    name: "Gravando mensagem de voz",
  })
  await expect(recorder).toBeVisible()
  await expect(
    recorder.getByRole("img", { name: "Gravação travada" })
  ).toBeVisible()
  await expect(
    hana.page.getByRole("main").getByText("gravando áudio…")
  ).toBeVisible()
  await expectNoSeriousA11yViolations(gil.page)
  await gil.page.waitForTimeout(1500)
  await recorder.getByRole("button", { name: "Parar e ouvir" }).click()
  await expect(gil.page.getByLabel("Pré-escuta do áudio")).toBeVisible()
  await gil.page.getByRole("button", { name: "Enviar áudio" }).click()

  // Segunda: enviada direto durante a gravação; a primeira gravação cancelada não chega.
  await gil.page.getByRole("button", { name: "Gravar mensagem de voz" }).click()
  await gil.page.waitForTimeout(500)
  await gil.page.getByRole("button", { name: "Cancelar gravação" }).click()
  await gil.page.getByRole("button", { name: "Gravar mensagem de voz" }).click()
  await gil.page.waitForTimeout(1500)
  await gil.page.getByRole("button", { name: "Enviar áudio" }).click()

  const plays = hana.page.getByRole("button", { name: "Tocar áudio" })
  await expect(plays).toHaveCount(2)
  await expectNoSeriousA11yViolations(hana.page)

  // Transcrição sob demanda (AU-03): o provedor é simulado; a busca real é coberta pelo TranscriptionIT.
  await hana.context.route("**/api/v1/media/*/transcript", (route) =>
    route.request().method() === "POST"
      ? route.fulfill({
          json: { mediaId: "x", text: "Oi, chego às oito", source: "GROQ" },
        })
      : route.continue()
  )
  await hana.page.getByRole("button", { name: "Transcrever" }).first().click()
  await expect(hana.page.getByText("Oi, chego às oito")).toBeVisible()

  // Toca o primeiro em 1,5× e o segundo começa sozinho quando ele acaba.
  await plays.first().click()
  const speed = hana.page.getByRole("button", { name: /^Velocidade 1×/ })
  await speed.click()
  await expect(
    hana.page.getByRole("button", { name: /^Velocidade 1,5×/ })
  ).toBeVisible()
  await expect(
    hana.page.getByRole("button", { name: "Pausar áudio" })
  ).toHaveCount(1)
  // A faixa atual passa para o segundo áudio (a barra de posição do segundo fica ativa).
  await expect(
    hana.page.getByRole("slider", { name: "Posição do áudio" }).nth(1)
  ).toBeEnabled({ timeout: 15_000 })

  // Fora da conversa, o mini-player continua com o áudio.
  await openMenuItem(hana.page, "Mensagens favoritas")
  const mini = hana.page.getByRole("region", { name: "Áudio tocando" })
  await expect(mini).toBeVisible()
  await expectNoSeriousA11yViolations(hana.page)
  await mini.getByRole("button", { name: "Fechar o tocador" }).click()
  await expect(mini).toBeHidden()

  await Promise.all([gil.context.close(), hana.context.close()])
})
