import type { Locator, Page } from "@playwright/test"

import {
  expect,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  test,
} from "./fixtures"
import { openMenuItem, pdf, pngFile, RUN, twoFriends } from "./support"

function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

/** Arrasta (dragenter/dragover e, com `drop`, solta) arquivos sobre o elemento, como o sistema faria. */
async function drag(
  target: Locator,
  files: { name: string; type: string; base64: string }[],
  steps: string[]
) {
  await target.evaluate(
    (element, { files, steps }) => {
      const data = new DataTransfer()
      for (const file of files) {
        const bytes = Uint8Array.from(atob(file.base64), (c) => c.charCodeAt(0))
        data.items.add(new File([bytes], file.name, { type: file.type }))
      }
      for (const type of steps) {
        element.dispatchEvent(
          new DragEvent(type, {
            bubbles: true,
            cancelable: true,
            dataTransfer: data,
          })
        )
      }
    },
    { files, steps }
  )
}

/**
 * Pedido do dono de 2026-10-29: anexos como no WhatsApp Web. Arrastar mostra a área de soltar; a prévia ocupa a
 * conversa com o arquivo em destaque (a 1ª página do PDF e quantas páginas), legenda por arquivo, miniaturas com "+",
 * o enviar com a contagem e "Deseja descartar a seleção?" ao fechar.
 */
test("arrastar arquivos abre a prévia do WhatsApp com legenda por arquivo", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}w`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Anexo")
  const main = gil.page.locator('[data-slot="message-scroller"]')
  const png = await pngFile(gil.page, "#2563eb", "azul.png")
  const image = {
    name: png.name,
    type: png.mimeType,
    base64: png.buffer.toString("base64"),
  }
  const nota = {
    name: "nota.pdf",
    type: "application/pdf",
    base64: pdf(["Primeira", "Segunda"]).toString("base64"),
  }

  // A área de soltar aparece ao arrastar e some ao sair.
  await drag(main, [image], ["dragenter", "dragover"])
  await expect(gil.page.getByText("Solte os arquivos aqui")).toBeVisible()
  await drag(main, [image], ["dragleave"])
  await expect(gil.page.getByText("Solte os arquivos aqui")).toBeHidden()

  // Soltar o PDF: a prévia mostra a primeira página e "2 páginas".
  await drag(main, [nota], ["dragenter", "dragover", "drop"])
  await expect(gil.page.getByText("Solte os arquivos aqui")).toBeHidden()
  const editor = gil.page.getByRole("dialog", { name: "Enviar 1 arquivo" })
  await expect(editor).toBeVisible()
  await expect(editor.getByText("2 páginas")).toBeVisible()
  await expect(editor.getByRole("img", { name: "nota.pdf" })).toBeVisible()
  await editor.getByLabel("Legenda (opcional)").fill("o contrato")

  // O "+" junta mais um arquivo, que ganha o destaque com a própria legenda.
  const chooser = gil.page.waitForEvent("filechooser")
  await editor.getByRole("button", { name: "Adicionar arquivos" }).click()
  await (await chooser).setFiles(png)
  const both = gil.page.getByRole("dialog", { name: "Enviar 2 arquivos" })
  await expect(both.getByRole("img", { name: "azul.png" })).toBeVisible()
  await expect(
    both.getByRole("button", { name: "Mostrar azul.png" })
  ).toHaveAttribute("aria-current", "true")
  await expect(both.getByLabel("Legenda (opcional)")).toHaveValue("")
  await both.getByLabel("Legenda (opcional)").fill("a foto")
  await both.getByRole("button", { name: "Mostrar nota.pdf" }).click()
  await expect(both.getByLabel("Legenda (opcional)")).toHaveValue("o contrato")
  await expectNoSeriousA11yViolations(gil.page)
  await expectNoHorizontalScroll(gil.page)

  // Fechar pergunta; Cancelar mantém tudo.
  await both.getByRole("button", { name: "Fechar" }).click()
  const confirm = gil.page.getByRole("alertdialog", {
    name: "Deseja descartar a seleção?",
  })
  await expect(confirm).toBeVisible()
  await confirm.getByRole("button", { name: "Cancelar" }).click()
  await expect(confirm).toBeHidden()
  await expect(both).toBeVisible()

  // Tirar um e pôr de volta soltando por cima da prévia aberta.
  await both.getByRole("button", { name: "Mostrar azul.png" }).click()
  await both.getByRole("button", { name: "Tirar da seleção" }).click()
  await expect(
    gil.page.getByRole("dialog", { name: "Enviar 1 arquivo" })
  ).toBeVisible()
  // Por cima da prévia aberta o arquivo solto se junta aos outros.
  await drag(
    gil.page.getByRole("dialog", { name: "Enviar 1 arquivo" }),
    [image],
    ["dragenter", "dragover", "drop"]
  )
  await both.getByLabel("Legenda (opcional)").fill("a foto")
  await both.getByRole("button", { name: "Enviar" }).click()
  await expect(both).toBeHidden()

  // Cada arquivo chega com a legenda dele.
  await expect(row(hana.page, "o contrato")).toContainText("nota.pdf", {
    timeout: 30_000,
  })
  await expect(
    row(hana.page, "a foto").getByRole("button", {
      name: "Abrir imagem azul.png",
    })
  ).toBeVisible({ timeout: 30_000 })

  // Uma imagem sem legenda fecha sem perguntar.
  await drag(main, [image], ["dragenter", "dragover", "drop"])
  const single = gil.page.getByRole("dialog", { name: "Enviar 1 arquivo" })
  await single.getByRole("button", { name: "Fechar" }).click()
  await expect(single).toBeHidden()
  await expect(gil.page.getByRole("alertdialog")).toHaveCount(0)
})

/**
 * Pedido do dono de 2026-10-29: o "@" no grupo abre a lista de membros (com "todos" no topo), filtra pelo que se
 * digita, escolhe pelo teclado ou clique e a pessoa mencionada vê o "@" na lista de conversas e o nome na bolha.
 * A resposta mostra a citação dentro da caixa do campo, como no WhatsApp.
 */
test("menção com @ no grupo e citação dentro da caixa", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}k`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Mencao")
  const groupName = `Turma ${tag}`

  await gil.page.goto("/")
  await openMenuItem(gil.page, "Novo grupo")
  const dialog = gil.page.getByRole("dialog", { name: "Novo grupo" })
  await dialog.getByLabel("Nome do grupo").fill(groupName)
  await dialog.getByRole("checkbox", { name: /Hana Mencao/ }).check()
  await dialog.getByRole("button", { name: "Criar grupo" }).click()
  await expect(gil.page.getByRole("heading", { name: groupName })).toBeVisible()
  await hana.page.goto("/")

  const field = gil.page.getByRole("textbox", { name: "Mensagem" })
  const list = gil.page.getByRole("listbox", { name: "Mencionar alguém" })
  // Dentro de um e-mail o "@" não abre nada.
  await field.pressSequentially("ana@")
  await expect(list).toBeHidden()
  await field.fill("")
  await field.pressSequentially("@")
  await expect(list).toBeVisible()
  await expect(list.getByRole("option").first()).toContainText(
    "Mencione todos os membros do grupo"
  )
  await expect(list.getByRole("option", { name: /Hana Mencao/ })).toBeVisible()
  await expect(list.getByRole("option", { name: /Gil Mencao/ })).toHaveCount(0)
  await expectNoSeriousA11yViolations(gil.page)
  // Esc fecha até o próximo "@"; filtrar pelo nome e escolher com Enter.
  await field.press("Escape")
  await expect(list).toBeHidden()
  await field.fill("Oi ")
  await field.pressSequentially("@han")
  await expect(list.getByRole("option")).toHaveCount(1)
  await field.press("Enter")
  await expect(field).toHaveValue(`Oi @hana.${tag} `)
  await field.pressSequentially("tudo bem?")
  await gil.page.getByRole("button", { name: "Enviar" }).click()
  await expect(row(gil.page, "tudo bem?")).toContainText("@Hana Mencao")

  // A Hana vê o "@" na lista e o próprio nome na bolha.
  const item = hana.page.getByRole("link", { name: new RegExp(groupName) })
  await expect(item.getByText("Menção a você")).toBeAttached()
  await item.click()
  const mentioned = row(hana.page, "tudo bem?")
  await expect(
    mentioned.getByRole("link", { name: "@Hana Mencao" })
  ).toBeVisible()

  // Responder: a citação fica dentro da caixa, acima do campo, com o X.
  await mentioned.hover()
  await mentioned.getByRole("button", { name: "Ações da mensagem" }).click()
  await hana.page
    .getByRole("menuitem", { name: "Responder", exact: true })
    .click()
  const box = hana.page.locator("form", {
    has: hana.page.getByRole("textbox", { name: "Mensagem" }),
  })
  const quote = box.getByText("Respondendo a Gil Mencao")
  await expect(quote).toBeVisible()
  const quoteBox = (await quote.boundingBox())!
  const fieldBox = (await hana.page
    .getByRole("textbox", { name: "Mensagem" })
    .boundingBox())!
  expect(quoteBox.y).toBeLessThan(fieldBox.y)
  expect(quoteBox.x).toBeGreaterThanOrEqual(fieldBox.x - 120)
  await expectNoSeriousA11yViolations(hana.page)
  await box.getByRole("button", { name: "Cancelar resposta" }).click()
  await expect(quote).toBeHidden()
  await hana.page.goto("/")
  await expect(
    hana.page.getByRole("link", { name: new RegExp(groupName) })
  ).not.toContainText("Menção a você")
})
