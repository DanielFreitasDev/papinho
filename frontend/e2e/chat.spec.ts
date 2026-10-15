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
  await expect(first.getByRole("img", { name: "Enviada" })).toBeVisible()

  // A Bia não tem a Ana nos contatos: a mensagem chega como solicitação, em tempo real.
  await pageB.getByRole("link", { name: "1 solicitação de mensagem" }).click()
  await pageB.getByRole("link", { name: /Ana Conversa/ }).click()
  await expect(pageB.getByText(/não está nos seus contatos/)).toBeVisible()
  await expectNoSeriousA11yViolations(pageB)
  await pageB.getByRole("button", { name: "Aceitar" }).click()
  await expect(pageB.getByText(/não está nos seus contatos/)).toBeHidden()

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

  await expectNoSeriousA11yViolations(pageA)
  await expectNoHorizontalScroll(pageA)
  expect(pageErrors).toEqual([])
  await contextA.close()
  await contextB.close()
})
