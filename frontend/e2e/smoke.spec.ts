import {
  expect,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  test,
} from "./fixtures"

test.describe("fumaça", () => {
  test("sem sessão, a tela inicial leva para a entrada", async ({
    page,
    pageErrors,
  }) => {
    await page.goto("/")

    await expect(page).toHaveURL(/\/(login|register)/)
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible()
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

    await expect(page.getByRole("heading", { level: 1 })).toBeVisible()
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
    await expect(page).toHaveURL(/\/(login|register)/)
    await expectNoSeriousA11yViolations(page)
    expect(pageErrors).toEqual([])
  })
})
