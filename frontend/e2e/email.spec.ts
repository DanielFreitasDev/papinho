import {
  expect,
  expectNoSeriousA11yViolations,
  test,
  watchErrors,
} from "./fixtures"
import {
  createInvite,
  ensureAdmin,
  loginThroughUi,
  PASSWORD,
  registerThroughUi,
  RUN,
} from "./support"

/**
 * E-mail opcional de recuperação (ID-05) contra um SMTP de verdade: a instância sobe com o e-mail ligado
 * apontando para um Mailpit, e PAPINHO_E2E_MAILPIT_URL diz onde ler as mensagens.
 */
const mailpit = process.env.PAPINHO_E2E_MAILPIT_URL

type MailpitSearch = { messages: { ID: string }[] }
type MailpitMessage = { Text: string }

/** Link mais recente enviado ao endereço (com o token no fragmento). */
async function latestLink(
  request: import("@playwright/test").APIRequestContext,
  to: string,
  path: string
): Promise<string> {
  let link = ""
  await expect
    .poll(
      async () => {
        const search = (await (
          await request.get(`${mailpit}/api/v1/search`, {
            params: { query: `to:"${to}"` },
          })
        ).json()) as MailpitSearch
        for (const { ID } of search.messages) {
          const message = (await (
            await request.get(`${mailpit}/api/v1/message/${ID}`)
          ).json()) as MailpitMessage
          const found = message.Text.match(
            new RegExp(`https?://\\S+${path}#token=[A-Za-z0-9_-]+`)
          )
          if (found) {
            link = found[0]
            return true
          }
        }
        return false
      },
      { timeout: 15_000 }
    )
    .toBe(true)
  return link
}

test.describe("e-mail de recuperação", () => {
  test.skip(!mailpit, "só com PAPINHO_E2E_MAILPIT_URL (instância com e-mail)")

  test("confirma o e-mail e redefine a senha pelo link", async ({
    browser,
    pageErrors,
    request,
  }) => {
    const suffix = test.info().project.name.startsWith("mobile") ? "m" : "d"
    const username = `eva.${RUN}${suffix}`
    const email = `eva.${RUN}${suffix}@exemplo.test`

    const admin = await ensureAdmin(browser, pageErrors)
    const invite = await createInvite(admin.page)
    await admin.context.close()

    const context = await browser.newContext()
    const page = await context.newPage()
    await watchErrors(page, pageErrors)
    await page.goto(invite)
    await registerThroughUi(page, username, "Eva Email")

    await page.goto("/settings/security")
    await page.getByLabel("E-mail", { exact: true }).fill(email)
    await page.locator("#security-email-password").fill(PASSWORD)
    await page
      .getByRole("button", { name: "Salvar e enviar confirmação" })
      .click()
    await expect(page.getByText("Pendente")).toBeVisible()
    await expectNoSeriousA11yViolations(page)

    await page.goto(await latestLink(request, email, "/verify-email"))
    await expect(page.getByText("E-mail confirmado.")).toBeVisible()
    // O token sai da barra de endereços assim que é lido.
    expect(page.url()).not.toContain("token=")
    await page.goto("/settings/security")
    await expect(page.getByText("Confirmado", { exact: true })).toBeVisible()

    const other = await browser.newContext()
    const forgot = await other.newPage()
    await watchErrors(forgot, pageErrors)
    await forgot.goto("/login")
    await forgot.getByRole("link", { name: "Esqueci minha senha" }).click()
    await forgot
      .getByRole("link", { name: "Receber um link por e-mail" })
      .click()
    await forgot.getByLabel("@usuario").fill(`@${username}`)
    await forgot.getByRole("button", { name: "Enviar link" }).click()
    await expect(forgot.getByText("Pedido recebido")).toBeVisible()
    await expectNoSeriousA11yViolations(forgot)

    const newPassword = "nova senha do e-mail 789"
    await forgot.goto(await latestLink(request, email, "/reset-password"))
    await forgot.getByLabel("Nova senha").fill(newPassword)
    await forgot.getByLabel("Confirmar senha").fill(newPassword)
    await expectNoSeriousA11yViolations(forgot)
    await forgot.getByRole("button", { name: "Redefinir senha" }).click()
    await expect(
      forgot.getByRole("heading", { name: "Entrar no Papinho" })
    ).toBeVisible()

    // A sessão antiga caiu; a senha antiga não serve e a nova entra.
    await page.reload()
    await expect(
      page.getByRole("heading", { name: "Entrar no Papinho" })
    ).toBeVisible()
    await loginThroughUi(forgot, username, newPassword)
    await expect(
      forgot.getByRole("complementary", { name: "Conversas e contatos" })
    ).toBeVisible()
    await context.close()
    await other.close()
  })
})
