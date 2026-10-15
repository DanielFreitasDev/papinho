import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { ADMIN, registerThroughUi } from "./support"

/** ID-08: numa instância vazia, a primeira conta é criada pela UI sem convite e vira admin. */
test("primeira conta da instância vira admin", async ({ page, pageErrors }) => {
  await page.goto("/")
  const heading = page.getByRole("heading", { level: 1 })
  await expect(heading).toHaveText(/Criar conta|Entrar no Papinho/)
  test.skip(
    (await heading.textContent()) !== "Criar conta",
    "instância já tem admin (execução repetida)"
  )
  await expect(page.getByText("Primeiro acesso")).toBeVisible()
  await expectNoSeriousA11yViolations(page)
  await registerThroughUi(page, ADMIN, "Anfitrião")
  await page.goto("/admin")
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible()
  expect(pageErrors).toEqual([])
})
