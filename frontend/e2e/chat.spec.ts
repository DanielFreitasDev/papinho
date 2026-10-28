import type { Page } from "@playwright/test"

import {
  expect,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  test,
  watchErrors,
} from "./fixtures"
import { createInvite, ensureAdmin, registerThroughUi, RUN } from "./support"

/** Linha da mensagem pelo texto do próprio corpo (não pela citação de uma resposta). */
function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

async function openActions(page: Page, text: string) {
  // O menu anterior ainda sai com animação: com dois abertos, "Selecionar" e "Fixar" aparecem duas vezes.
  await expect(page.getByRole("menu")).toHaveCount(0)
  const target = row(page, text)
  await target.hover()
  await target.getByRole("button", { name: "Ações da mensagem" }).click()
}

async function send(page: Page, text: string) {
  await page.getByRole("textbox", { name: "Mensagem" }).fill(text)
  await page.getByRole("button", { name: "Enviar" }).click()
}

/**
 * Fase 2: conversa individual em tempo real entre dois navegadores — solicitação de mensagem (CT-02),
 * formatação (MS-01), status e leitura (MS-02), digitando (MS-03), responder, reagir e fixar (MS-04), presença
 * (RT-03) e o marcador de não lidas (MS-14).
 */
test("duas pessoas conversam em tempo real, da solicitação à leitura", async ({
  browser,
  pageErrors,
}) => {
  // Fluxo longo com dois navegadores; sob carga paralela passa dos 30 s padrão.
  test.slow()
  const suffix = test.info().project.name.startsWith("mobile") ? "m" : "d"
  const ana = `ana.${RUN}${suffix}`
  const bia = `bia.${RUN}${suffix}`
  const owner = await ensureAdmin(browser, pageErrors)
  const invite = await createInvite(owner.page)
  await owner.context.close()

  const contextA = await browser.newContext()
  const pageA = await contextA.newPage()
  await watchErrors(pageA, pageErrors)
  await pageA.goto(invite)
  await registerThroughUi(pageA, ana, "Ana Conversa")

  const contextB = await browser.newContext()
  const pageB = await contextB.newPage()
  await watchErrors(pageB, pageErrors)
  await pageB.goto(invite)
  await registerThroughUi(pageB, bia, "Bia Conversa")

  // Ana adiciona a Bia e abre a conversa pelo contato.
  await pageA.getByRole("button", { name: "Adicionar contato" }).click()
  const add = pageA.getByRole("dialog", { name: "Adicionar contato" })
  await add.getByLabel("@usuario").fill(bia)
  await add.getByRole("button", { name: "Adicionar", exact: true }).click()
  await expect(add).toBeHidden()
  await pageA
    .getByRole("complementary")
    .getByRole("tab", { name: "Contatos" })
    .click()
  await pageA
    .getByRole("button", { name: "Conversar com Bia Conversa" })
    .click()
  await expect(
    pageA.getByRole("heading", { name: "Bia Conversa" })
  ).toBeVisible()
  await send(pageA, "Oi *Bia*! ||segredo||")
  const first = row(pageA, "Bia!")
  await expect(first.locator("strong", { hasText: "Bia" })).toBeVisible()
  // A Bia está com o app aberto: o tique pode virar "Entregue" antes de a asserção rodar.
  await expect(
    first.getByRole("img", { name: /^(Enviada|Entregue)$/ })
  ).toBeVisible()

  // A Bia não tem a Ana nos contatos: a mensagem chega como solicitação, em tempo real.
  await pageB.getByRole("link", { name: "1 solicitação de mensagem" }).click()
  await pageB.getByRole("link", { name: /Ana Conversa/ }).click()
  await expect(pageB.getByText(/não está nos seus contatos/)).toBeVisible()
  await expectNoSeriousA11yViolations(pageB)
  await pageB.getByRole("button", { name: "Aceitar" }).click()
  await expect(pageB.getByText(/não está nos seus contatos/)).toBeHidden()
  // Aceita, a Bia já pode pôr a Ana nos contatos (pedido do dono de 2026-10-28).
  const offer = pageB.getByRole("alertdialog", {
    name: "Adicionar Ana Conversa aos contatos?",
  })
  await expect(offer).toBeVisible()
  await expect(offer).toContainText("só para os seus contatos")
  await expectNoSeriousA11yViolations(pageB)
  await offer.getByRole("button", { name: "Adicionar aos contatos" }).click()
  await expect(offer).toBeHidden()
  await expect(pageB.getByText("Contato adicionado")).toBeVisible()
  const contacts = await pageB.request.get("/api/v1/contacts")
  expect(await contacts.json()).toEqual([
    expect.objectContaining({ username: ana }),
  ])
  await expect(
    pageB.getByRole("main").getByText(/não está nos seus contatos/)
  ).toHaveCount(0)

  // Spoiler só aparece ao tocar; a leitura da Bia chega para a Ana como dois tiques azuis.
  const spoiler = row(pageB, "Bia!").getByRole("button", {
    name: /Spoiler oculto/,
  })
  await expect(spoiler).toBeVisible()
  await spoiler.click()
  await expect(spoiler).toBeHidden()
  await expect(row(pageB, "Bia!").getByText("segredo")).toBeVisible()
  await expect(first.getByRole("img", { name: "Lida" })).toBeVisible()
  await expect(
    pageA.getByRole("main").getByText("online", { exact: true })
  ).toBeVisible()

  // Digitando… e resposta com citação.
  await pageB
    .getByRole("textbox", { name: "Mensagem" })
    .pressSequentially("Tud")
  await expect(pageA.getByRole("main").getByText("digitando…")).toBeVisible()
  await openActions(pageB, "Bia!")
  await pageB.getByRole("menuitem", { name: "Responder" }).click()
  await expect(pageB.getByText("Respondendo a Ana Conversa")).toBeVisible()
  await send(pageB, "Tudo ótimo com `código`")
  const reply = row(pageA, "Tudo ótimo")
  await expect(reply.locator("code", { hasText: "código" })).toBeVisible()
  await expect(
    reply.getByRole("button", { name: /Ir para a mensagem respondida/ })
  ).toBeVisible()

  // Reação e mensagem fixada chegam para a outra pessoa pelo afterSeq.
  await openActions(pageA, "Tudo ótimo")
  await pageA.getByRole("menuitem", { name: "Reagir com 👍" }).click()
  await expect(
    row(pageB, "Tudo ótimo").getByLabel("Reações").getByText("👍")
  ).toBeVisible()
  await openActions(pageA, "Bia!")
  await pageA.getByRole("menuitem", { name: "Fixar" }).click()
  await expect(
    pageB.getByRole("button", { name: /Mensagens fixadas/ })
  ).toBeVisible()

  // Dados da mensagem: entregue e lida, com horário (MS-02).
  await openActions(pageA, "Bia!")
  await pageA.getByRole("menuitem", { name: "Dados da mensagem" }).click()
  const info = pageA.getByRole("dialog", { name: "Dados da mensagem" })
  await expect(info.getByText("Bia Conversa")).toBeVisible()
  await expect(info.getByText("Lida")).toBeVisible()
  await expect(info.getByText("Ainda não")).toHaveCount(0)
  await pageA.keyboard.press("Escape")

  // Favoritar (MS-04) aparece em Mensagens favoritas.
  await openActions(pageA, "Tudo ótimo")
  await pageA.getByRole("menuitem", { name: "Favoritar" }).click()
  await expect(
    row(pageA, "Tudo ótimo").getByRole("img", { name: "Favoritada" })
  ).toBeVisible()

  // Selecionar várias e encaminhar: chegam para a Bia com o selo.
  await openActions(pageA, "Bia!")
  await pageA.getByRole("menuitem", { name: "Selecionar" }).click()
  await expect(pageA.getByText("1 selecionada")).toBeVisible()
  await row(pageA, "Tudo ótimo").getByRole("checkbox").check()
  await expect(pageA.getByText("2 selecionadas")).toBeVisible()
  await pageA.getByRole("button", { name: "Encaminhar" }).click()
  const forward = pageA.getByRole("dialog", { name: "Encaminhar para…" })
  await forward.getByRole("checkbox").first().check()
  await forward.getByRole("button", { name: "Encaminhar" }).click()
  await expect(forward).toBeHidden()
  await expect(pageB.getByText("Encaminhada", { exact: true })).toHaveCount(2)

  await expectNoSeriousA11yViolations(pageA)
  await expectNoHorizontalScroll(pageA)

  // Não lidas (MS-14): com a Bia fora da conversa, duas mensagens novas viram contador, marcador e atalho.
  await pageB.goto("/")
  await send(pageA, "Primeira nova")
  await send(pageA, "Segunda nova")
  await expect(pageB.getByText("2 mensagens não lidas")).toBeVisible()
  await pageB.getByRole("link", { name: /Ana Conversa/ }).click()
  await expect(
    pageB.getByRole("main").getByText("2 mensagens não lidas")
  ).toBeVisible()
  await pageB
    .getByRole("button", { name: "Ir para a primeira não lida" })
    .click()
  await expect(row(pageB, "Primeira nova")).toBeInViewport()
  await pageB.goto("/")
  await pageB
    .getByRole("button", { name: "Ações da conversa: Ana Conversa" })
    .click()
  await pageB.getByRole("menuitem", { name: "Marcar como não lida" }).click()
  await expect(pageB.getByText("Marcada como não lida")).toBeAttached()

  await pageA.goto("/starred")
  await expect(pageA.getByText("Tudo ótimo com código")).toBeVisible()
  expect(pageErrors).toEqual([])
  await contextA.close()
  await contextB.close()
})
