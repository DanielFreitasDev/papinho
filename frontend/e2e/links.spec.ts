import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { RUN, twoFriends } from "./support"

/**
 * Prévia de link (MD-04) e YouTube "clique para carregar" (MD-05). A busca no servidor é coberta pelo
 * LinkPreviewIT; aqui a resposta da API é fixa para não depender da internet, e nada do YouTube é baixado
 * antes do clique.
 */
test("prévia de link e vídeo do YouTube que só carrega no clique", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}l`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Link")
  const youtubeRequests: string[] = []
  await hana.context.route("**/api/v1/link-previews?*", async (route) => {
    const url = new URL(route.request().url()).searchParams.get("url")!
    const youtube = url.includes("youtu")
    await route.fulfill({
      json: {
        url,
        available: true,
        title: youtube ? "Clipe da banda" : "Receita de cuscuz",
        description: youtube ? null : "Com manteiga da terra",
        siteName: youtube ? "YouTube" : "Cozinha",
        imageUrl: null,
        embedProvider: youtube ? "YOUTUBE" : null,
        embedSrc: youtube
          ? "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"
          : null,
      },
    })
  })
  await hana.context.route("https://www.youtube-nocookie.com/**", (route) => {
    youtubeRequests.push(route.request().url())
    return route.fulfill({
      contentType: "text/html",
      body: "<!doctype html><title>player</title>",
    })
  })

  for (const text of [
    "olha https://cozinha.example/cuscuz",
    "e esse https://youtu.be/dQw4w9WgXcQ",
  ]) {
    await gil.page.getByRole("textbox", { name: "Mensagem" }).fill(text)
    await gil.page.getByRole("button", { name: "Enviar" }).click()
  }

  const article = hana.page.getByRole("link", { name: /Receita de cuscuz/ })
  await expect(article).toBeVisible()
  await expect(article).toHaveAttribute("rel", /noopener/)
  const load = hana.page.getByRole("button", {
    name: "Carregar YouTube: Clipe da banda",
  })
  await expect(load).toBeVisible()
  await expect(hana.page.locator("iframe")).toHaveCount(0)
  expect(youtubeRequests).toEqual([])
  await expectNoSeriousA11yViolations(hana.page)

  await load.click()
  const player = hana.page.locator("iframe[title='Player: Clipe da banda']")
  await expect(player).toHaveAttribute(
    "src",
    "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1"
  )
  await expect.poll(() => youtubeRequests.length).toBeGreaterThan(0)

  await Promise.all([gil.context.close(), hana.context.close()])
})
