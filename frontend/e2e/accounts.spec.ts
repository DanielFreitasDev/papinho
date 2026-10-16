import {
  expect,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  test,
  watchErrors,
} from "./fixtures"
import {
  ADMIN,
  createInvite,
  ensureAdmin,
  loginThroughUi,
  openContactsTab,
  openMenuItem,
  PASSWORD,
  registerThroughUi,
  RUN,
  totp,
} from "./support"

/**
 * Fase 1 (QA-03 "cadastro por convite"): primeiro admin, convite usado por outra pessoa em outro contexto
 * do navegador, contatos por @usuario e por link, TOTP e passkey.
 */
test.describe.serial("contas e contatos", () => {
  let inviteUrl = ""
  // Um @usuario por projeto (desktop e celular rodam o mesmo fluxo na mesma instância).
  const suffix = () =>
    test.info().project.name.startsWith("mobile") ? "m" : "d"
  const brunoName = () => `bruno.${RUN}${suffix()}`

  test("admin cria convite e a outra pessoa se cadastra por ele em outro contexto", async ({
    browser,
    pageErrors,
  }) => {
    const bruno = brunoName()
    const admin = await ensureAdmin(browser, pageErrors)
    await expectNoSeriousA11yViolations(admin.page)
    inviteUrl = await createInvite(admin.page)

    const friend = await browser.newContext()
    const page = await friend.newPage()
    await watchErrors(page, pageErrors)
    await page.goto(inviteUrl)
    await expect(
      page.getByRole("heading", { name: "Criar conta" })
    ).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    await registerThroughUi(page, bruno, "Bruno Teste")
    await expect(page.getByText(`@${bruno}`)).toBeVisible()

    await page.getByRole("button", { name: "Adicionar contato" }).click()
    const dialog = page.getByRole("dialog", { name: "Adicionar contato" })
    await dialog.getByLabel("@usuario").fill(`@${ADMIN.toUpperCase()}`)
    await dialog.getByLabel("Apelido (só você vê)").fill("Chefe")
    await dialog.getByRole("button", { name: "Adicionar", exact: true }).click()
    await openContactsTab(page)
    await expect(
      page.getByRole("complementary").getByText("Chefe")
    ).toBeVisible()

    await admin.page.goto(`/add/${bruno}`)
    await expect(
      admin.page.getByRole("heading", { name: "Bruno Teste" })
    ).toBeVisible()
    await admin.page
      .getByRole("main")
      .getByRole("button", { name: "Adicionar", exact: true })
      .click()
    await expect(
      admin.page.getByText("Já está nos seus contatos")
    ).toBeVisible()
    // No celular a lista só aparece na tela inicial (navegação em pilha, UI-01).
    await admin.page.goto("/")
    await openContactsTab(admin.page)
    await expect(
      admin.page.getByRole("complementary").getByText(`@${bruno}`)
    ).toBeVisible()

    expect(pageErrors).toEqual([])
    await friend.close()
    await admin.context.close()
  })

  test("sem convite, o cadastro explica que é preciso um convite", async ({
    page,
    pageErrors,
  }) => {
    await page.goto("/register")
    await expect(page.getByText("Convite necessário")).toBeVisible()
    await page.goto("/register?invite=convite-falso")
    await expect(page.getByText("Convite inválido")).toBeVisible()
    expect(pageErrors).toEqual([])
  })

  test("login errado mostra mensagem uniforme; TOTP passa a ser exigido depois de ativado", async ({
    page,
    pageErrors,
  }) => {
    const bruno = brunoName()
    await loginThroughUi(page, bruno, "senha errada qualquer")
    await expect(page.getByRole("alert")).toHaveText(
      "Usuário ou senha incorretos."
    )

    await loginThroughUi(page, bruno)
    await expect(
      page.getByRole("complementary", { name: "Conversas e contatos" })
    ).toBeVisible()
    await page.goto("/settings/security")
    await expectNoSeriousA11yViolations(page)
    await page.getByRole("button", { name: "Ativar" }).click()
    const dialog = page.getByRole("dialog", {
      name: "Verificação em duas etapas (TOTP)",
    })
    const secret = (await dialog.locator("code").textContent())!.trim()
    await dialog
      .getByLabel("Código de verificação")
      .pressSequentially(totp(secret))
    await dialog.getByRole("button", { name: "Confirmar" }).click()
    await expect(page.getByText("Ligada")).toBeVisible()

    await openMenuItem(page, "Sair")
    await loginThroughUi(page, bruno)
    await expect(page.getByLabel("Código de verificação")).toBeVisible()
    await page
      .getByLabel("Código de verificação")
      .pressSequentially(totp(secret))
    await page.getByRole("button", { name: "Entrar", exact: true }).click()
    await expect(
      page.getByRole("complementary", { name: "Conversas e contatos" })
    ).toBeVisible()
    expect(pageErrors).toEqual([])
  })

  test("passkey: cadastra com autenticador virtual e entra sem senha", async ({
    page,
    browserName,
    pageErrors,
  }) => {
    test.skip(
      browserName !== "chromium",
      "autenticador virtual só existe no Chromium (CDP)"
    )
    const cdp = await page.context().newCDPSession(page)
    await cdp.send("WebAuthn.enable")
    await cdp.send("WebAuthn.addVirtualAuthenticator", {
      options: {
        protocol: "ctap2",
        transport: "internal",
        hasResidentKey: true,
        hasUserVerification: true,
        isUserVerified: true,
      },
    })

    const admin = `passkey.${RUN}${suffix()}`
    // Conta nova para o teste não depender do TOTP do teste anterior.
    const owner = await ensureAdmin(page.context().browser()!, pageErrors)
    const invite = await createInvite(owner.page)
    await owner.context.close()
    await page.goto(invite)
    await registerThroughUi(page, admin, "Pessoa Passkey")

    await page.goto("/settings/security")
    await page.getByLabel("Nome da passkey").fill("Notebook de teste")
    await page.getByRole("button", { name: "Adicionar passkey" }).click()
    await expect(page.getByText("Notebook de teste")).toBeVisible()

    await openMenuItem(page, "Sair")
    await page.goto("/login")
    await page.getByRole("button", { name: "Entrar com passkey" }).click()
    await expect(
      page.getByRole("complementary", { name: "Conversas e contatos" })
    ).toBeVisible()
    await expect(page.getByText(`@${admin}`)).toBeVisible()

    await page.goto("/settings/sessions")
    await expect(page.getByText("Esta sessão")).toBeVisible()
    expect(pageErrors).toEqual([])
  })

  test("telas de entrada e o app cabem em 320 px sem rolagem horizontal", async ({
    browser,
  }) => {
    const context = await browser.newContext({
      viewport: { width: 320, height: 640 },
    })
    const page = await context.newPage()
    await page.goto("/login")
    await expectNoHorizontalScroll(page)
    await expectNoSeriousA11yViolations(page)
    await page.getByLabel("@usuario").fill(brunoName())
    await page.getByLabel("Senha", { exact: true }).fill(PASSWORD)
    await page.goto("/register")
    await expectNoHorizontalScroll(page)
    await context.close()
  })
})
