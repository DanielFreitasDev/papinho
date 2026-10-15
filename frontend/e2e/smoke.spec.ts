import {
  expect,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  test,
} from "./fixtures"

test.describe("fumaça", () => {
  test("tela inicial carrega do JAR com o servidor disponível", async ({
    page,
    pageErrors,
  }) => {
    await page.goto("/")

    await expect(
      page.getByRole("heading", { name: "Papinho", level: 1 })
    ).toBeVisible()
    await expect(page.getByRole("status")).toHaveText("Servidor disponível")
    await expect(page.locator("html")).toHaveAttribute("lang", "pt-BR")
    await expectNoSeriousA11yViolations(page)
    expect(pageErrors).toEqual([])
  })

  test("funciona em 320 px sem rolagem horizontal", async ({
    page,
    pageErrors,
  }) => {
    await page.setViewportSize({ width: 320, height: 640 })
    await page.goto("/")

    await expect(page.getByRole("status")).toBeVisible()
    await expectNoHorizontalScroll(page)
    expect(pageErrors).toEqual([])
  })

  test("rota inexistente mostra o estado de não encontrado com volta ao início", async ({
    page,
    pageErrors,
  }) => {
    await page.goto("/rota/que/nao/existe")

    await expect(page.getByText("Página não encontrada")).toBeVisible()
    await page.getByRole("link", { name: "Ir para o início" }).click()
    await expect(page).toHaveURL("/")
    await expectNoSeriousA11yViolations(page)
    expect(pageErrors).toEqual([])
  })
})
