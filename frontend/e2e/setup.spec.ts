import {
  expect,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  test,
} from "./fixtures"

/**
 * Página /setup da PKI da LAN (PK-03). Só existe com o perfil lan; informe a URL da porta HTTP em
 * PAPINHO_E2E_SETUP_URL (por exemplo http://papinho.lan/setup) para rodar.
 */
const setupUrl = process.env.PAPINHO_E2E_SETUP_URL

test.describe("página de instalação da CA", () => {
  test.skip(!setupUrl, "PAPINHO_E2E_SETUP_URL não informado")

  test("mostra impressão digital, downloads e instruções sem violar a CSP", async ({
    page,
    pageErrors,
  }) => {
    await page.goto(setupUrl!)

    await expect(
      page.getByRole("heading", { name: "Conectar ao Papinho com segurança" })
    ).toBeVisible()
    await expect(page.locator("#fingerprint")).toHaveText(
      /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/
    )
    await expect(
      page.getByRole("link", { name: "Baixar papinho-ca.crt" })
    ).toHaveAttribute("href", "/setup/ca.crt")
    await expect(
      page.getByRole("img", { name: "QR Code da página de instalação" })
    ).toBeVisible()
    await expect(page.locator("#detected")).toContainText("Detectamos")
    await expectNoSeriousA11yViolations(page)
    expect(pageErrors).toEqual([])
  })

  test("cabe em 320 px", async ({ page, pageErrors }) => {
    await page.setViewportSize({ width: 320, height: 640 })
    await page.goto(setupUrl!)

    await expect(page.locator("#fingerprint")).toBeVisible()
    await expectNoHorizontalScroll(page)
    expect(pageErrors).toEqual([])
  })
})
