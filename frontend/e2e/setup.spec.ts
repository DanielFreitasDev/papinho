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

  test("UI-05: sem seta, dica nem barra de rolagem do navegador, nos dois temas", async ({
    page,
    pageErrors,
  }) => {
    await page.goto(setupUrl!)
    await page.locator("details summary").first().click()
    for (const theme of ["light", "dark"] as const) {
      await page.emulateMedia({ colorScheme: theme })
      const native = await page.evaluate(() => {
        const found: string[] = []
        document.querySelectorAll("summary").forEach((summary) => {
          if (getComputedStyle(summary).listStyleType !== "none") {
            found.push(`seta nativa: ${summary.textContent}`)
          }
        })
        document.querySelectorAll("[title]").forEach((element) => {
          found.push(`title nativo: ${element.outerHTML.slice(0, 80)}`)
        })
        const html = getComputedStyle(document.documentElement)
        if (
          CSS.supports("scrollbar-color", "red blue") &&
          html.scrollbarColor === "auto"
        ) {
          found.push("barra de rolagem padrão")
        }
        return found
      })
      expect(native).toEqual([])
      const info = test.info()
      const path = info.outputPath(`setup-${theme}.png`)
      await page.screenshot({ path, fullPage: true })
      await info.attach(`${info.project.name}-setup-${theme}`, {
        path,
        contentType: "image/png",
      })
    }
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
