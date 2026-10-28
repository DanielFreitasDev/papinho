import {
  expect,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  test,
  watchErrors,
} from "./fixtures"
import { createInvite, ensureAdmin, registerThroughUi, RUN } from "./support"

/**
 * Pedido do dono de 2026-10-28: no "Adicionar contato", enquanto se digita, aparecem as pessoas da instância pelo
 * nome, como na busca de perfis do Facebook e do Instagram; dá para adicionar e começar a conversa. Quem desliga
 * "Aparecer na busca por nome" (Privacidade) só aparece pelo @usuario completo.
 */
test("busca pessoas pelo nome ao adicionar contato", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const suffix = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}`
  const owner = await ensureAdmin(browser, pageErrors)
  const invite = await createInvite(owner.page)
  await owner.context.close()
  const people = []
  for (const [username, name] of [
    [`joana.${suffix}`, `Joana Alves ${suffix}`],
    [`luis.${suffix}`, `Luís Barbosa ${suffix}`],
    [`mara.${suffix}`, `Mara Costa ${suffix}`],
  ]) {
    const context = await browser.newContext()
    const page = await context.newPage()
    await watchErrors(page, pageErrors)
    await page.goto(invite)
    await registerThroughUi(page, username, name)
    people.push({ context, page, username, name })
  }
  const [joana, luis, mara] = people

  // Mara sai da busca por nome nas configurações de privacidade.
  await mara.page.goto("/settings/privacy")
  const optOut = mara.page.getByRole("switch", {
    name: "Aparecer na busca por nome",
  })
  await expect(optOut).toBeChecked()
  await optOut.click()
  await expect(optOut).not.toBeChecked()
  await expect(mara.page.getByText("Alterações salvas")).toBeVisible()

  // Sem acento e por pedaços do nome e do sobrenome, como numa rede social.
  const page = joana.page
  await page.getByRole("button", { name: "Adicionar contato" }).click()
  const dialog = page.getByRole("dialog", { name: "Adicionar contato" })
  const search = dialog.getByRole("searchbox", { name: "Nome ou @usuario" })
  await search.fill(`luis barb ${suffix}`)
  const add = dialog.getByRole("button", { name: `Adicionar ${luis.name}` })
  await expect(add).toBeVisible()
  await expect(dialog.getByText(`@${luis.username}`)).toBeVisible()
  await expectNoSeriousA11yViolations(page)
  await expectNoHorizontalScroll(page)
  await add.click()
  await expect(
    page.getByText(`${luis.name} está nos seus contatos`)
  ).toBeVisible()

  // Quem saiu da busca por nome não aparece pelo nome, só pelo @usuario completo.
  await search.fill(`mara costa ${suffix}`)
  await expect(dialog.getByText("Ninguém encontrado")).toBeVisible()
  await search.fill(`@${mara.username}`)
  await expect(
    dialog.getByRole("button", { name: `Adicionar ${mara.name}` })
  ).toBeVisible()

  // Contato já adicionado: "Conversar" abre a conversa e fecha o diálogo.
  await search.fill(`luis ${suffix}`)
  await dialog
    .getByRole("button", { name: `Conversar com ${luis.name}` })
    .click()
  await expect(dialog).toBeHidden()
  await expect(
    page.getByRole("main").getByRole("heading", { name: luis.name })
  ).toBeVisible()

  await Promise.all(people.map((person) => person.context.close()))
})
