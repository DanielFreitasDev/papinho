import type { Locator, Page } from "@playwright/test"

import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { RUN, twoFriends } from "./support"

function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

async function send(page: Page, text: string) {
  await page.getByRole("textbox", { name: "Mensagem" }).fill(text)
  await page.getByRole("button", { name: "Enviar" }).click()
  await expect(
    row(page, text).getByRole("img", { name: /^(Enviada|Entregue|Lida)$/ })
  ).toBeVisible()
}

/** No celular a lista fica na tela inicial; no desktop está sempre ao lado. */
async function showList(page: Page) {
  const back = page.getByRole("link", { name: "Voltar" })
  if (await back.isVisible()) {
    await back.click()
  }
  return page.getByRole("complementary", { name: "Conversas e contatos" })
}

/** Bolha que pisca depois do salto (index.css, [data-flash]). */
function flashing(message: Locator) {
  return message.locator('[data-slot="bubble"][data-flash]')
}

/**
 * Destaque da busca (GR-06): o termo fica no marca-texto, sem diferenciar acentos e maiúsculas e pelo radical (como a
 * busca do servidor), nas mensagens e nos resultados; anterior e próximo com contador; o resultado aberto leva à
 * mensagem, que pisca, com a busca da conversa preenchida e o termo fora da URL.
 */
test("destaca o termo buscado nas mensagens e nos resultados", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}h`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Destaque")
  for (const text of [
    "A Ação de amanhã está confirmada",
    "Reunião às 10h",
    "Reuniões semanais começam na segunda",
    `<img src=x onerror="alert(1)"> sem ação nenhuma`,
  ]) {
    await send(gil.page, text)
  }
  await expect(row(hana.page, "Reuniões semanais")).toBeVisible()

  // Na conversa: "acao" marca "Ação" (e o texto que parecia HTML continua texto).
  await hana.page.getByRole("button", { name: "Buscar nesta conversa" }).click()
  const inside = hana.page.getByRole("search", {
    name: "Buscar nesta conversa",
  })
  const box = inside.getByRole("searchbox")
  await box.fill("acao")
  await expect(inside.getByRole("status")).toHaveText("1 de 2")
  await expect(
    row(hana.page, "confirmada").locator("mark[data-search-match]")
  ).toHaveText("Ação")
  const hostile = row(hana.page, "onerror")
  await expect(hostile.locator("mark[data-search-match]")).toHaveText("ação")
  await expect(hostile.locator("img")).toHaveCount(0)
  // O marca-texto passa no contraste do axe nos dois temas.
  await expectNoSeriousA11yViolations(hana.page)
  await hana.page.emulateMedia({ colorScheme: "dark" })
  await expectNoSeriousA11yViolations(hana.page)
  await hana.page.emulateMedia({ colorScheme: "light" })

  // Plural e radical como no servidor: "reunião" marca "Reunião" e "Reuniões"; anterior/próximo com contador.
  await box.fill("reunião")
  await expect(inside.getByRole("status")).toHaveText("1 de 2")
  await expect(
    row(hana.page, "Reuniões semanais").locator("mark[data-search-match]")
  ).toHaveText("Reuniões")
  await expect(
    row(hana.page, "Reunião às 10h").locator("mark[data-search-match]")
  ).toHaveText("Reunião")
  await inside.getByRole("button", { name: "Resultado mais antigo" }).click()
  await expect(inside.getByRole("status")).toHaveText("2 de 2")
  await expect(flashing(row(hana.page, "Reunião às 10h"))).toBeVisible()
  await inside.getByRole("button", { name: "Resultado mais recente" }).click()
  await expect(inside.getByRole("status")).toHaveText("1 de 2")
  await expect(flashing(row(hana.page, "Reuniões semanais"))).toBeVisible()

  // Com movimento reduzido, a bolha não anima (contorno parado).
  await hana.page.emulateMedia({ reducedMotion: "reduce" })
  await inside.getByRole("button", { name: "Resultado mais antigo" }).click()
  const still = flashing(row(hana.page, "Reunião às 10h"))
  await expect(still).toBeVisible()
  expect(
    await still
      .locator('[data-slot="bubble-content"]')
      .evaluate((element) => getComputedStyle(element, "::after").animationName)
  ).toBe("none")
  await hana.page.emulateMedia({ reducedMotion: "no-preference" })
  await box.press("Escape")
  await expect(inside).toBeHidden()
  await expect(hana.page.locator("mark[data-search-match]")).toHaveCount(0)

  // Busca global: o resultado mostra o termo marcado; abrir leva à mensagem, que pisca, com a busca preenchida.
  const list = await showList(hana.page)
  await list
    .getByRole("searchbox", { name: "Buscar conversas, contatos e mensagens" })
    .fill("ACAO")
  const result = list
    .getByRole("region", { name: "Mensagens" })
    .getByRole("link", { name: /confirmada/ })
  await expect(result.locator("mark[data-search-match]")).toHaveText("Ação")
  await expectNoSeriousA11yViolations(hana.page)
  await result.click()
  await expect(flashing(row(hana.page, "confirmada"))).toBeVisible()
  await expect(
    hana.page
      .getByRole("search", { name: "Buscar nesta conversa" })
      .getByRole("searchbox")
  ).toHaveValue("ACAO")
  await expect(
    row(hana.page, "confirmada").locator("mark[data-search-match]")
  ).toHaveText("Ação")
  expect(hana.page.url()).not.toMatch(/acao/i)

  await Promise.all([gil.context.close(), hana.context.close()])
})

/** Distância entre a última linha da conversa e o fim da área visível, e a altura do espaçador do MessageScroller. */
function bottomGap(page: Page) {
  return page.evaluate(() => {
    const viewport = document.querySelector<HTMLElement>(
      '[data-slot="message-scroller-viewport"]'
    )!
    const spacer = viewport.querySelector<HTMLElement>(
      "[data-message-scroller-spacer]"
    )
    const rows = viewport.querySelectorAll('[data-slot="message"]')
    const last = rows[rows.length - 1].getBoundingClientRect()
    return {
      spacer: !spacer || spacer.hidden ? 0 : spacer.offsetHeight,
      below: Math.round(viewport.getBoundingClientRect().bottom - last.bottom),
    }
  })
}

/**
 * Bug relatado pelo dono em 2026-10-28: a busca da conversa que caía na última mensagem a "levantava" para o meio da
 * tela e deixava um vão embaixo, que só sumia fechando e abrindo a conversa. Para centralizar o alvo, o
 * MessageScroller cria um espaçador no fim da lista e só o tira ao rolar até o fim pelo botão.
 */
test("salto da busca para a última mensagem não deixa vão embaixo", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}u`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Vao")
  for (let index = 1; index <= 12; index++) {
    await send(gil.page, `Mensagem ${index} para encher a conversa`)
  }
  await send(gil.page, "A última fala do abacaxi")
  await expect(row(hana.page, "abacaxi")).toBeVisible()
  await expect
    .poll(async () => (await bottomGap(hana.page)).below)
    .toBeLessThan(40)

  await hana.page.getByRole("button", { name: "Buscar nesta conversa" }).click()
  const inside = hana.page.getByRole("search", {
    name: "Buscar nesta conversa",
  })
  await inside.getByRole("searchbox").fill("abacaxi")
  await expect(inside.getByRole("status")).toHaveText("1 de 1")
  await expect(flashing(row(hana.page, "abacaxi"))).toBeVisible()
  // A última mensagem fica no fim da tela, sem espaço vazio embaixo, também depois de a correção do salto terminar.
  await hana.page.waitForTimeout(1_000)
  expect(await bottomGap(hana.page)).toEqual({
    spacer: 0,
    below: expect.any(Number),
  })
  expect((await bottomGap(hana.page)).below).toBeLessThan(40)

  // Uma mensagem do meio vai para o centro; voltar à última e fechar a busca não deixa vão.
  await inside.getByRole("searchbox").fill("encher")
  await expect(inside.getByRole("status")).toHaveText(/^1 de 1[0-9]$/)
  await inside.getByRole("button", { name: "Resultado mais antigo" }).click()
  await expect(inside.getByRole("status")).toHaveText(/^2 de 1[0-9]$/)
  await inside.getByRole("searchbox").press("Escape")
  await expect(inside).toBeHidden()
  await send(hana.page, "Respondo depois da busca")
  await expect
    .poll(async () => (await bottomGap(hana.page)).below)
    .toBeLessThan(40)
  expect((await bottomGap(hana.page)).spacer).toBe(0)

  await Promise.all([gil.context.close(), hana.context.close()])
})
