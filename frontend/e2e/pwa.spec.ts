import type { Page } from "@playwright/test"

import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { ensureAdmin, RUN, twoFriends } from "./support"

function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

async function send(page: Page, text: string) {
  await page.getByRole("textbox", { name: "Mensagem" }).fill(text)
  await page.getByRole("button", { name: "Enviar" }).click()
}

/** Espera o service worker assumir a página (a primeira visita só o instala). */
async function controlled(page: Page) {
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready
  })
  await page.reload()
  await expect
    .poll(() =>
      page.evaluate(() => navigator.serviceWorker.controller !== null)
    )
    .toBe(true)
}

/** Fase 6: PWA instalável (NT-04) e uso offline com fila de envio (NT-05). */
test("PWA instalável, offline com conversas recentes e fila de texto", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}w`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Offline")

  // Manifesto completo, com o tipo certo, ícone maskable e capturas de tela.
  const manifest = await gil.page.request.get("/manifest.webmanifest")
  expect(manifest.headers()["content-type"]).toContain(
    "application/manifest+json"
  )
  const json = (await manifest.json()) as {
    display: string
    icons: { purpose?: string }[]
    screenshots: unknown[]
  }
  expect(json.display).toBe("standalone")
  expect(json.icons.some((icon) => icon.purpose === "maskable")).toBe(true)
  expect(json.screenshots.length).toBeGreaterThan(0)

  await send(gil.page, "Antes de cair a rede")
  await expect(row(hana.page, "Antes de cair a rede")).toBeVisible()
  await controlled(gil.page)
  await expect(row(gil.page, "Antes de cair a rede")).toBeVisible()

  // Nenhuma resposta da API vai para o Cache Storage: só o app.
  const cached = await gil.page.evaluate(async () => {
    const urls: string[] = []
    for (const name of await caches.keys()) {
      const cache = await caches.open(name)
      urls.push(...(await cache.keys()).map((request) => request.url))
    }
    return urls
  })
  expect(cached.length).toBeGreaterThan(0)
  expect(
    cached.filter((url) => new URL(url).pathname.startsWith("/api"))
  ).toEqual([])

  // Sem rede: aviso visível e o texto fica na fila, "enviando".
  await gil.context.setOffline(true)
  await expect(gil.page.getByText(/Sem conexão/)).toBeVisible()
  await send(gil.page, "Escrito sem rede")
  await expect(
    row(gil.page, "Escrito sem rede").getByRole("img", { name: "Enviando" })
  ).toBeVisible()
  await expectNoSeriousA11yViolations(gil.page)

  // Recarregar sem rede: o app abre pelo service worker e as conversas vêm do IndexedDB.
  await gil.page.reload()
  await expect(row(gil.page, "Antes de cair a rede")).toBeVisible()
  await expect(row(gil.page, "Escrito sem rede")).toBeVisible()
  await expect(gil.page.getByText(/Sem conexão/)).toBeVisible()

  // A rede volta: a fila sai com o mesmo clientMessageId e chega uma vez só.
  await gil.context.setOffline(false)
  await expect(row(hana.page, "Escrito sem rede")).toHaveCount(1)
  await expect(
    row(gil.page, "Escrito sem rede").getByRole("img", {
      name: /^(Enviada|Entregue|Lida)$/,
    })
  ).toBeVisible()
  await expect(gil.page.getByText(/Sem conexão/)).toBeHidden()

  // Sair da conta apaga os dados offline dela.
  const before = await gil.page.evaluate(async () =>
    (await indexedDB.databases()).map((db) => db.name)
  )
  expect(before.some((name) => name?.startsWith("papinho-"))).toBe(true)
  await gil.page.goto("/")
  await gil.page.getByRole("button", { name: "Menu" }).click()
  await gil.page.getByRole("menuitem", { name: "Sair" }).click()
  await expect(gil.page).toHaveURL(/\/login/)
  await expect
    .poll(() =>
      gil.page.evaluate(async () =>
        (await indexedDB.databases())
          .map((db) => db.name)
          .filter((name) => name?.startsWith("papinho-"))
      )
    )
    .toEqual([])

  await gil.context.close()
  await hana.context.close()
})

/**
 * NT-04: uma versão nova do service worker instala, mas espera. A página avisa "Nova versão" e só troca quando a
 * pessoa clica em "Atualizar" (sem skipWaiting cego); aí o novo assume e a página recarrega.
 */
test("versão nova do app espera o Atualizar para assumir", async ({
  browser,
  pageErrors,
}) => {
  const { context, page } = await ensureAdmin(browser, pageErrors)
  await controlled(page)
  const registration = () =>
    page.evaluate(async () => {
      const current = await navigator.serviceWorker.getRegistration()
      return {
        waiting: current?.waiting?.state ?? null,
        active: current?.active?.state ?? null,
      }
    })

  // Chega uma versão nova. O route do Playwright não alcança a busca que o navegador faz para atualizar o
  // service worker, então a versão nova vem por outro endereço do mesmo script: para o navegador, é um worker novo
  // na mesma registration, instalado e esperando, como depois de um deploy.
  await page.evaluate(async () => {
    await navigator.serviceWorker.register("/sw.js?versao=nova", { scope: "/" })
  })
  await expect(page.getByText("Nova versão do Papinho")).toBeVisible()
  // Nada troca sozinho: o novo fica esperando enquanto a pessoa não escolhe.
  await expect.poll(registration).toEqual({
    waiting: "installed",
    active: "activated",
  })
  await expectNoSeriousA11yViolations(page)

  const reloaded = page.waitForEvent("load")
  await page.getByRole("button", { name: "Atualizar" }).click()
  await reloaded
  await expect
    .poll(registration)
    .toEqual({ waiting: null, active: "activated" })
  await expect(
    page.getByRole("complementary", { name: "Conversas e contatos" })
  ).toBeVisible()
  await context.close()
})

/** Fase 6: notificações com o app aberto (NT-01) e configurações por tipo e som (NT-03), com avisos (NT-02). */
test("notificações pelo navegador com as preferências do servidor", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}n`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Notifica")
  await hana.context.grantPermissions(["notifications"])
  // Guarda as notificações mostradas (pela página ou pelo service worker) para conferir.
  await hana.context.addInitScript(() => {
    const shown: { title: string; body?: string; silent?: boolean }[] = []
    ;(window as unknown as { shown: typeof shown }).shown = shown
    ServiceWorkerRegistration.prototype.showNotification = async function (
      title: string,
      options?: NotificationOptions
    ) {
      shown.push({
        title,
        body: options?.body,
        silent: options?.silent ?? undefined,
      })
    }
  })

  await hana.page.goto("/settings/notifications")
  await expect(
    hana.page.getByText("Notificações permitidas neste navegador.")
  ).toBeVisible()
  await expect(hana.page.getByText("O push depende de internet")).toBeVisible()
  await expectNoSeriousA11yViolations(hana.page)
  await hana.page.getByRole("switch", { name: /^Som/ }).click()
  await expect(hana.page.getByText("Alterações salvas").first()).toBeVisible()

  // Hana está nas configurações, não na conversa: a mensagem do Gil vira notificação, sem som.
  await send(gil.page, "Chegou aviso?")
  await expect
    .poll(() =>
      hana.page.evaluate(
        () =>
          (
            window as unknown as {
              shown: { title: string; body?: string; silent?: boolean }[]
            }
          ).shown
      )
    )
    .toContainEqual({ title: gil.name, body: "Chegou aviso?", silent: true })

  await gil.context.close()
  await hana.context.close()
})
