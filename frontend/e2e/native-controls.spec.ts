import { readFile } from "node:fs/promises"
import type { Page } from "@playwright/test"

import {
  expect,
  expectNoSeriousA11yViolations,
  test,
  watchErrors,
} from "./fixtures"
import {
  chooseAttachment,
  ensureAdmin,
  openMenuItem,
  PASSWORD,
  RUN,
  twoFriends,
} from "./support"

type Theme = "light" | "dark"

/**
 * O que ainda tem a aparência do navegador na página (UI-05): controles nativos visíveis, dicas por title,
 * áreas roláveis sem as cores do tema e color-scheme diferente do tema do app. A lista precisa ficar vazia.
 * Elementos escondidos de propósito (o input real dentro do Checkbox, do Slider e do Select do Base UI, o
 * <input type="file"> acionado por botão) não contam.
 */
async function nativeAppearance(page: Page, theme: Theme): Promise<string[]> {
  return page.evaluate((expected) => {
    const found: string[] = []
    const describe = (element: Element) =>
      element.outerHTML.replace(/\s+/g, " ").slice(0, 140)
    const shown = (element: Element) => {
      const box = element.getBoundingClientRect()
      const style = getComputedStyle(element)
      return (
        box.width > 2 &&
        box.height > 2 &&
        style.visibility !== "hidden" &&
        Number(style.opacity) > 0.01 &&
        style.clip !== "rect(0px, 0px, 0px, 0px)" &&
        !style.clipPath.startsWith("inset(50%")
      )
    }
    const forbidden = [
      "select",
      "progress",
      "meter",
      "details",
      "audio[controls]",
      "video[controls]",
      ...[
        "checkbox",
        "radio",
        "range",
        "date",
        "time",
        "datetime-local",
        "month",
        "week",
        "color",
        "file",
      ].map((type) => `input[type="${type}"]`),
    ].join(", ")
    document.querySelectorAll(forbidden).forEach((element) => {
      if (shown(element)) {
        found.push(`controle nativo: ${describe(element)}`)
      }
    })
    // Setinhas do type=number e botão de limpar do type=search (UI-05): o CSS base tira as duas aparências.
    document
      .querySelectorAll<HTMLInputElement>(
        'input[type="number"], input[type="search"]'
      )
      .forEach((element) => {
        if (!shown(element)) {
          return
        }
        const allowed =
          element.type === "number" ? ["textfield", "none"] : ["none"]
        const appearance = getComputedStyle(element).appearance
        if (!allowed.includes(appearance)) {
          found.push(`aparência ${appearance}: ${describe(element)}`)
        }
        const parts =
          element.type === "number"
            ? ["::-webkit-inner-spin-button", "::-webkit-outer-spin-button"]
            : ["::-webkit-search-cancel-button"]
        for (const part of parts) {
          const style = getComputedStyle(element, part)
          if (
            /spin-button|cancel-button/.test(style.appearance) &&
            style.display !== "none"
          ) {
            found.push(`${part} nativo: ${describe(element)}`)
          }
        }
      })
    document.querySelectorAll("[title]:not(iframe)").forEach((element) => {
      found.push(`title nativo: ${describe(element)}`)
    })

    // Barras de rolagem: scrollbar-color/-width onde o motor entende; senão, a regra ::-webkit-scrollbar.
    const standard = CSS.supports("scrollbar-color", "red blue")
    if (!standard) {
      const selectors: string[] = []
      const walk = (rules: CSSRuleList) => {
        for (const rule of Array.from(rules)) {
          if (rule instanceof CSSStyleRule) {
            selectors.push(rule.selectorText)
          }
          if ("cssRules" in rule) {
            walk((rule as CSSGroupingRule).cssRules)
          }
        }
      }
      for (const sheet of Array.from(document.styleSheets)) {
        walk(sheet.cssRules)
      }
      if (
        !selectors.some((text) => text.includes("::-webkit-scrollbar-thumb"))
      ) {
        found.push("sem regra ::-webkit-scrollbar-thumb para as barras")
      }
    }
    for (const element of Array.from(document.querySelectorAll("*"))) {
      const style = getComputedStyle(element)
      const scrolls =
        (/(auto|scroll)/.test(style.overflowY) &&
          element.scrollHeight > element.clientHeight + 1) ||
        (/(auto|scroll)/.test(style.overflowX) &&
          element.scrollWidth > element.clientWidth + 1)
      if (
        scrolls &&
        shown(element) &&
        standard &&
        (style.scrollbarColor === "auto" || style.scrollbarWidth === "auto")
      ) {
        found.push(`barra de rolagem padrão: ${describe(element)}`)
      }
    }

    const scheme = getComputedStyle(document.documentElement).colorScheme
    if (scheme !== expected) {
      found.push(`color-scheme "${scheme}" no tema ${expected}`)
    }
    return found
  }, theme)
}

/**
 * No WebKit, cada page.screenshot() do Playwright injeta um <style> que a CSP estrita do app recusa (comprovado numa
 * página mínima: uma violação por captura, nenhuma no Chromium e no Firefox). Essas linhas são da ferramenta.
 */
const SCREENSHOT_CSP = new Set([
  "console: Refused to apply a stylesheet because its hash, its nonce, or 'unsafe-inline' does not appear in the style-src directive of the Content Security Policy.",
  "console: CSP: style-src-elem inline :5 ",
])

/** Tira do registro as violações que a captura do WebKit acabou de gerar (as do app continuam reprovando). */
async function forgetScreenshotCsp(errors: string[], from: number) {
  for (let wait = 0; wait < 20; wait++) {
    if (errors.slice(from).some((error) => SCREENSHOT_CSP.has(error))) {
      break
    }
    await new Promise((resolve) => setTimeout(resolve, 100))
  }
  for (let index = errors.length - 1; index >= from; index--) {
    if (SCREENSHOT_CSP.has(errors[index])) {
      errors.splice(index, 1)
    }
  }
}

/** Verificação do UI-05 numa tela, com a captura anexada ao relatório (navegador, tema e tela no nome). */
async function verify(
  page: Page,
  theme: Theme,
  screen: string,
  errors: string[]
) {
  // Popups e diálogos ainda entrando atrapalham a captura: espera as animações finitas.
  await page.evaluate(() =>
    Promise.race([
      Promise.all(
        document
          .getAnimations()
          .filter(
            (animation) =>
              animation.playState === "running" &&
              animation.effect?.getComputedTiming().iterations !== Infinity
          )
          .map((animation) => animation.finished.catch(() => undefined))
      ),
      new Promise((resolve) => setTimeout(resolve, 1_000)),
    ])
  )
  // Arquivo na pasta do teste (test-results) e anexo no relatório HTML, para revisão visual.
  const info = test.info()
  const path = info.outputPath(`${theme}-${screen}.png`)
  const before = errors.length
  await page.screenshot({ path, caret: "initial" })
  if (page.context().browser()?.browserType().name() === "webkit") {
    await forgetScreenshotCsp(errors, before)
  }
  await info.attach(`${info.project.name}-${theme}-${screen}`, {
    path,
    contentType: "image/png",
  })
  expect(await nativeAppearance(page, theme), screen).toEqual([])
}

/**
 * Troca de tela por carga completa só com a rede parada: o WebKit reporta como erro de página ("due to access
 * control checks") as buscas canceladas pela navegação, coisa que Chromium e Firefox não fazem.
 */
async function goto(page: Page, path: string) {
  await page.waitForLoadState("networkidle")
  await page.goto(path)
}

/** PNG desenhado num canvas do próprio navegador. */
async function pngFile(page: Page, name: string) {
  const dataUrl = await page.evaluate(() => {
    const canvas = document.createElement("canvas")
    canvas.width = 320
    canvas.height = 200
    const context = canvas.getContext("2d")!
    context.fillStyle = "#0ea5e9"
    context.fillRect(0, 0, 320, 200)
    context.fillStyle = "#f59e0b"
    context.fillRect(40, 40, 120, 120)
    return canvas.toDataURL("image/png")
  })
  return {
    name,
    mimeType: "image/png",
    buffer: Buffer.from(dataUrl.split(",")[1], "base64"),
  }
}

async function asset(name: string, mimeType: string) {
  return {
    name,
    mimeType,
    buffer: await readFile(new URL(`./assets/${name}`, import.meta.url)),
  }
}

function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

/**
 * UI-05: lista de conversas, conversa com foto, vídeo, áudio e documento, formulários, configurações e modais
 * nos temas claro e escuro, em Chromium, Firefox e WebKit (projetos desktop-*). Cada tela vira uma captura no
 * relatório do Playwright e passa pela verificação de aparência nativa.
 */
test("nenhum controle com a aparência do navegador, nos temas claro e escuro", async ({
  browser,
  browserName,
  baseURL,
  pageErrors,
  isMobile,
}) => {
  test.skip(
    browserName === "webkit" && !baseURL?.startsWith("https:"),
    "WebKit recusa cookies Secure em http://localhost: defina PAPINHO_E2E_HTTPS_URL (instância do perfil lan)"
  )
  test.setTimeout(300_000)
  // O WebKit do Playwright no Linux (GStreamer) congela a página de vez em quando com vídeo na conversa, também
  // na versão anterior ao UI-05 (investigação pendente no STATUS). Lá o vídeo fica de fora; o tocador de vídeo é
  // coberto no Chromium e no Firefox.
  const withVideo = browserName !== "webkit"
  if (!withVideo) {
    test.info().annotations.push({
      type: "limitação",
      description:
        "WebKit sem anexo de vídeo: congelamento pré-existente do WebKit/GStreamer no Linux",
    })
  }
  const check = (page: Page, theme: Theme, screen: string) =>
    verify(page, theme, screen, pageErrors)
  const { gil, hana } = await twoFriends(
    browser,
    pageErrors,
    `ui${RUN}${browserName[0]}`,
    "Visual"
  )

  // Conteúdo da conversa: mensagens suficientes para rolar, foto, vídeo (fora do WebKit), áudio e documento.
  const field = gil.page.getByRole("textbox", { name: "Mensagem" })
  for (let index = 1; index <= 12; index++) {
    await field.fill(`Mensagem ${index}: *negrito*, _itálico_ e \`mono\``)
    // No celular o Enter quebra a linha: envia pelo botão.
    await gil.page.getByRole("button", { name: "Enviar" }).click()
  }
  await expect(row(hana.page, "Mensagem 12")).toBeVisible()
  const files = [
    await pngFile(gil.page, "quadro.png"),
    ...(withVideo ? [await asset("clipe.webm", "video/webm")] : []),
    await asset("tom.ogg", "audio/ogg"),
    {
      name: "relatorio.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4\n% relatorio\n" + "linha\n".repeat(50)),
    },
  ]
  const chooser = gil.page.waitForEvent("filechooser")
  await chooseAttachment(gil.page, "Documento")
  await (await chooser).setFiles(files)
  const send = gil.page.getByRole("dialog", {
    name: `Enviar ${files.length} arquivos`,
  })
  await send.getByRole("button", { name: "Enviar" }).click()
  await expect(send).toBeHidden()
  // A conversa reabre com tudo carregado: este spec verifica aparência; a chegada em tempo real é coberta pelos
  // specs de chat e mídia (no WebKit a mensagem nova às vezes só aparece ao reabrir, investigação no STATUS).
  // No celular a lista não aparece junto da conversa: espera o documento na própria conversa.
  await expect(
    isMobile
      ? hana.page.getByRole("link", { name: /relatorio\.pdf/ })
      : hana.page
          .getByRole("complementary", { name: "Conversas e contatos" })
          .getByText("Documento")
  ).toBeVisible({ timeout: 30_000 })
  await hana.page.waitForLoadState("networkidle")
  await hana.page.reload()
  await expect(
    hana.page.getByRole("link", { name: /relatorio\.pdf/ })
  ).toBeVisible({ timeout: 15_000 })
  await expect(
    hana.page.getByRole("button", { name: "Abrir imagem quadro.png" })
  ).toBeVisible()
  if (withVideo) {
    await expect(
      hana.page.getByRole("group", { name: "clipe.webm" })
    ).toBeVisible()
  }
  await expect(
    hana.page.getByRole("button", { name: "Tocar áudio" })
  ).toBeVisible()

  for (const theme of ["light", "dark"] as const) {
    await gil.page.emulateMedia({ colorScheme: theme })
    await hana.page.emulateMedia({ colorScheme: theme })

    // Lista de conversas e conversa com os tocadores do projeto.
    await check(hana.page, theme, "lista-e-conversa")
    if (browserName === "chromium") {
      await expectNoSeriousA11yViolations(hana.page)
    }

    // Busca na conversa: botão de limpar próprio e calendário no filtro de dia.
    await hana.page
      .getByRole("button", { name: "Buscar nesta conversa" })
      .click()
    const search = hana.page.getByRole("search", {
      name: "Buscar nesta conversa",
    })
    await search.getByRole("searchbox").fill("negrito")
    await expect(
      search.getByRole("button", { name: "Limpar a busca" })
    ).toBeVisible()
    await search.getByRole("button", { name: /^Dia: / }).click()
    await expect(hana.page.getByRole("grid")).toBeVisible()
    await check(hana.page, theme, "busca-com-calendario")
    await hana.page.keyboard.press("Escape")
    await expect(hana.page.getByRole("grid")).toBeHidden()
    await search.getByRole("searchbox").press("Escape")
    await expect(search).toBeHidden()

    // Agendar: dia no calendário e hora numa lista própria.
    await field.fill("Para mais tarde")
    await gil.page.getByRole("button", { name: "Mais opções de envio" }).click()
    await gil.page.getByRole("menuitem", { name: "Agendar envio" }).click()
    const schedule = gil.page.getByRole("dialog", { name: "Agendar mensagem" })
    await expect(schedule).toBeVisible()
    await check(gil.page, theme, "agendar")
    await schedule.getByRole("combobox", { name: "Hora" }).click()
    await expect(gil.page.getByRole("listbox")).toBeVisible()
    await check(gil.page, theme, "agendar-hora")
    await gil.page.keyboard.press("Escape")
    await expect(gil.page.getByRole("listbox")).toBeHidden()
    await schedule.getByRole("button", { name: /^Data: / }).click()
    await expect(gil.page.getByRole("grid")).toBeVisible()
    await check(gil.page, theme, "agendar-calendario")
    if (browserName === "chromium") {
      await expectNoSeriousA11yViolations(gil.page)
    }
    await gil.page.keyboard.press("Escape")
    await expect(gil.page.getByRole("grid")).toBeHidden()
    await gil.page.keyboard.press("Escape")
    await expect(schedule).toBeHidden()
    await field.fill("")

    // Enquete com prazo opcional.
    await chooseAttachment(gil.page, "Enquete")
    const poll = gil.page.getByRole("dialog", { name: "Nova enquete" })
    await expect(poll).toBeVisible()
    await check(gil.page, theme, "enquete")
    await gil.page.keyboard.press("Escape")
    await expect(poll).toBeHidden()

    // Seleção de mensagens e encaminhar: caixas de seleção do projeto.
    const first = row(gil.page, "Mensagem 1:")
    await first.hover()
    await first.getByRole("button", { name: "Ações da mensagem" }).click()
    await gil.page.getByRole("menuitem", { name: "Selecionar" }).click()
    await expect(gil.page.getByText("1 selecionada")).toBeVisible()
    await check(gil.page, theme, "selecao")
    await gil.page.getByRole("button", { name: "Encaminhar" }).click()
    const forward = gil.page.getByRole("dialog", { name: "Encaminhar para…" })
    await expect(forward.getByRole("checkbox").first()).toBeVisible()
    await check(gil.page, theme, "encaminhar")
    await gil.page.keyboard.press("Escape")
    await expect(forward).toBeHidden()
    await gil.page.getByRole("button", { name: "Cancelar seleção" }).click()

    // Emojis: busca com botão de limpar e nome no rodapé, sem title nativo.
    await gil.page.getByRole("button", { name: "Emojis" }).click()
    await gil.page.getByRole("searchbox", { name: "Buscar emoji" }).fill("sol")
    await gil.page
      .locator('[data-slot="popover-content"]')
      .getByRole("button", { name: /sol/i })
      .first()
      .hover()
    await check(gil.page, theme, "emojis")
    await gil.page.keyboard.press("Escape")

    // Galeria: miniatura do vídeo, tocador num diálogo e áudios no tocador global.
    await hana.page
      .getByRole("button", { name: /^Dados do contato: Gil Visual/ })
      .click()
    await expect(
      hana.page.getByRole("button", { name: "Mídia, links e docs" })
    ).toBeVisible()
    await check(hana.page, theme, "dados-do-contato")
    await hana.page
      .getByRole("button", { name: "Denunciar Gil Visual" })
      .click()
    const report = hana.page.getByRole("dialog", {
      name: "Denunciar Gil Visual?",
    })
    await expect(report.getByRole("checkbox")).toBeVisible()
    await check(hana.page, theme, "denunciar")
    await hana.page.keyboard.press("Escape")
    await expect(report).toBeHidden()
    await hana.page.getByRole("button", { name: "Mídia, links e docs" }).click()
    await expect(
      hana.page
        .getByRole("tabpanel", { name: "Mídia" })
        .getByRole("button", { name: "Abrir imagem quadro.png" })
    ).toBeVisible()
    if (withVideo) {
      await expect(
        hana.page.getByRole("button", { name: "Abrir vídeo clipe.webm" })
      ).toBeVisible()
    }
    await check(hana.page, theme, "galeria-midia")
    if (withVideo) {
      await hana.page
        .getByRole("button", { name: "Abrir vídeo clipe.webm" })
        .click()
      const video = hana.page.getByRole("dialog", { name: "clipe.webm" })
      await expect(
        video.getByRole("group", { name: "clipe.webm" })
      ).toBeVisible()
      await check(hana.page, theme, "galeria-video")
      if (browserName === "chromium") {
        await expectNoSeriousA11yViolations(hana.page)
      }
      await hana.page.keyboard.press("Escape")
      await expect(video).toBeHidden()
    }
    await hana.page.getByRole("tab", { name: "Áudios" }).click()
    await expect(
      hana.page
        .getByRole("tabpanel", { name: "Áudios" })
        .getByRole("button", { name: "Tocar áudio" })
    ).toBeVisible()
    await check(hana.page, theme, "galeria-audios")
    await hana.page.keyboard.press("Escape")

    // Novo grupo: busca e caixas de seleção dos contatos.
    await openMenuItem(gil.page, "Novo grupo")
    const group = gil.page.getByRole("dialog", { name: "Novo grupo" })
    await expect(group.getByRole("checkbox").first()).toBeVisible()
    await check(gil.page, theme, "novo-grupo")
    await gil.page.keyboard.press("Escape")
    await expect(group).toBeHidden()

    // Lista de transmissão (validade com data e hora próprias) e convite com QR Code.
    await openMenuItem(gil.page, "Nova lista de transmissão")
    const broadcast = gil.page.getByRole("dialog", {
      name: "Nova lista de transmissão",
    })
    await expect(broadcast.getByRole("checkbox").first()).toBeVisible()
    await check(gil.page, theme, "transmissao")
    await gil.page.keyboard.press("Escape")
    await expect(broadcast).toBeHidden()
    await openMenuItem(gil.page, "Convidar amigos")
    const invite = gil.page.getByRole("dialog", { name: "Convidar amigos" })
    await expect(invite).toBeVisible()
    await check(gil.page, theme, "convidar")
    await gil.page.keyboard.press("Escape")
    await expect(invite).toBeHidden()

    // Configurações: formulários, seletores, interruptores e campos de senha.
    for (const path of [
      "/settings/profile",
      "/settings/account",
      "/settings/security",
      "/settings/sessions",
      "/settings/privacy",
      "/settings/blocked",
      "/settings/appearance",
      "/settings/notifications",
      "/settings/chats",
      "/settings/transcription",
      "/starred",
      "/requests",
    ]) {
      await goto(gil.page, path)
      await expect(gil.page.getByRole("main")).toBeVisible()
      await expect(
        gil.page.getByRole("status", { name: /^Carregando/ })
      ).toHaveCount(0)
      await check(gil.page, theme, path.slice(1).replaceAll("/", "-"))
    }
    const admin = await ensureAdmin(browser, pageErrors)
    await goto(admin.page, "/admin")
    await admin.page.emulateMedia({ colorScheme: theme })
    await expect(admin.page.getByRole("main")).toBeVisible()
    await expect(
      admin.page.getByRole("status", { name: /^Carregando/ })
    ).toHaveCount(0)
    await check(admin.page, theme, "admin")
    await admin.context.close()
    await goto(gil.page, "/")
    await gil.page
      .getByRole("complementary", { name: "Conversas e contatos" })
      .getByRole("link", { name: /Hana Visual/ })
      .click()

    // Entrada: formulário com o botão próprio de mostrar a senha.
    // O Firefox ignora colorScheme nas opções do contexto e o emulateMedia feito antes da primeira navegação;
    // depois do goto ele vale nos três motores (o tema "sistema" acompanha a mudança).
    const visitor = await browser.newContext()
    const login = await visitor.newPage()
    await watchErrors(login, pageErrors)
    for (const path of ["/register", "/forgot-password", "/recover"]) {
      await goto(login, path)
      await login.emulateMedia({ colorScheme: theme })
      await expect(login.getByRole("main")).toBeVisible()
      await check(login, theme, path.slice(1))
    }
    await goto(login, "/login")
    await login.emulateMedia({ colorScheme: theme })
    await login.getByLabel("@usuario").fill(gil.username)
    await login.getByLabel("Senha", { exact: true }).fill(PASSWORD)
    await login.getByRole("button", { name: "Mostrar a senha" }).click()
    await expect(login.getByLabel("Senha", { exact: true })).toHaveAttribute(
      "type",
      "text"
    )
    await check(login, theme, "entrar")
    if (browserName === "chromium") {
      await expectNoSeriousA11yViolations(login)
    }
    await visitor.close()
  }

  await Promise.all([gil.context.close(), hana.context.close()])
})
