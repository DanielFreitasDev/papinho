import {
  expect,
  expectNoSeriousA11yViolations,
  test,
  watchErrors,
} from "./fixtures"
import {
  createInvite,
  ensureAdmin,
  openMenuItem,
  registerThroughUi,
  RUN,
} from "./support"

/**
 * Painel "Dados do contato" (UI-01) com bloquear (CT-03) e denunciar (CT-04), e a fila de denúncias do admin
 * (AD-01). No desktop o painel é a terceira coluna; no celular, uma gaveta.
 */
test("denúncia com bloqueio chega à fila do admin", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const suffix = test.info().project.name.startsWith("mobile") ? "m" : "d"
  const davi = `davi.${RUN}${suffix}`
  const eva = `eva.${RUN}${suffix}`
  const owner = await ensureAdmin(browser, pageErrors)
  const invite = await createInvite(owner.page)

  const contextD = await browser.newContext()
  const pageD = await contextD.newPage()
  await watchErrors(pageD, pageErrors)
  await pageD.goto(invite)
  await registerThroughUi(pageD, davi, "Davi Insistente")

  const contextE = await browser.newContext()
  const pageE = await contextE.newPage()
  await watchErrors(pageE, pageErrors)
  await pageE.goto(invite)
  await registerThroughUi(pageE, eva, "Eva Denuncia")

  // Davi escreve para a Eva, que não o tem nos contatos.
  await pageD.getByRole("button", { name: "Adicionar contato" }).click()
  const add = pageD.getByRole("dialog", { name: "Adicionar contato" })
  await add.getByLabel("@usuario").fill(eva)
  await add.getByRole("button", { name: "Adicionar", exact: true }).click()
  await expect(add).toBeHidden()
  await pageD
    .getByRole("complementary")
    .getByRole("tab", { name: "Contatos" })
    .click()
  await pageD
    .getByRole("button", { name: "Conversar com Eva Denuncia" })
    .click()
  for (const text of ["primeira mensagem", "compre agora"]) {
    await pageD.getByRole("textbox", { name: "Mensagem" }).fill(text)
    await pageD.getByRole("button", { name: "Enviar" }).click()
  }

  // Eva abre a solicitação, os dados do contato e denuncia bloqueando junto.
  await pageE.getByRole("link", { name: "1 solicitação de mensagem" }).click()
  await pageE.getByRole("link", { name: /Davi Insistente/ }).click()
  await expect(pageE.getByText("compre agora")).toBeVisible()
  await pageE
    .getByRole("button", { name: /^Dados do contato: Davi Insistente/ })
    .click()
  const details = pageE
    .getByRole("complementary", { name: "Dados do contato" })
    .or(pageE.getByRole("dialog", { name: "Dados do contato" }))
  await expect(details.getByText(`@${davi}`)).toBeVisible()
  await expectNoSeriousA11yViolations(pageE)
  await details
    .getByRole("button", { name: "Denunciar Davi Insistente" })
    .click()
  const report = pageE.getByRole("dialog", {
    name: "Denunciar Davi Insistente?",
  })
  await report.getByLabel("Motivo (opcional)").fill("propaganda")
  await expect(
    report.getByRole("checkbox", { name: "Bloquear Davi Insistente também" })
  ).toBeChecked()
  await report.getByRole("button", { name: "Denunciar" }).click()
  await expect(pageE.getByText("Denúncia enviada")).toBeVisible()
  await expect(
    details.getByRole("button", { name: "Desbloquear Davi Insistente" })
  ).toBeVisible()

  // Bloqueado: o Davi não consegue mais enviar.
  await pageD.getByRole("textbox", { name: "Mensagem" }).fill("oi?")
  await pageD.getByRole("button", { name: "Enviar" }).click()
  await expect(
    pageD.getByRole("button", { name: "Tentar enviar de novo" })
  ).toBeVisible()

  // O admin vê a denúncia com as mensagens copiadas e a resolve.
  await openMenuItem(owner.page, "Administração")
  await owner.page.getByRole("tab", { name: /Denúncias/ }).click()
  const card = owner.page
    .getByRole("listitem")
    .filter({ hasText: `@${davi} denunciado por @${eva}` })
  await expect(card.getByText("propaganda")).toBeVisible()
  await expect(card.getByText("compre agora")).toBeVisible()
  await expectNoSeriousA11yViolations(owner.page)
  await card.getByRole("button", { name: "Marcar como resolvida" }).click()
  await expect(card).toBeHidden()

  // Provedores de transcrição (AU-05): cadastrar um Whisper próprio; a chave nunca volta para a tela.
  await owner.page.getByRole("tab", { name: "Transcrição" }).click()
  await owner.page
    .getByRole("main")
    .getByRole("button", { name: "Adicionar", exact: true })
    .click()
  const provider = owner.page.getByRole("listitem").filter({ hasText: "Groq" })
  await provider.getByLabel("Chave da API").fill("chave-secreta-e2e")
  await provider.getByRole("button", { name: "Salvar" }).click()
  await expect(provider.getByText("chave configurada")).toBeVisible()
  await expect(provider.getByLabel("Chave da API")).toHaveValue("")
  await expectNoSeriousA11yViolations(owner.page)
  await provider.getByRole("button", { name: "Remover" }).click()
  await expect(provider).toBeHidden()

  await Promise.all([contextD.close(), contextE.close(), owner.context.close()])
})
