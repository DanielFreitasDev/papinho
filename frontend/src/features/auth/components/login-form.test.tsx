import { describe, expect, it, vi } from "vitest"
import { http, HttpResponse } from "msw"
import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"

import { server } from "@/test/msw/server"
import { renderWithProviders } from "@/test/test-utils"
import { LoginForm } from "./login-form"

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
}))

const me = {
  account: {
    id: "1",
    username: "ana",
    admin: false,
    totpEnabled: true,
    remainingRecoveryCodes: 10,
    createdAt: "",
    nextUsernameChangeAt: null,
  },
  profile: {
    userId: "1",
    displayName: "Ana",
    about: null,
    hasPhoto: false,
    updatedAt: "",
  },
}

describe("LoginForm", () => {
  it("mostra o erro uniforme do servidor sem limpar o @usuario", async () => {
    server.use(
      http.post("/api/v1/auth/login", () =>
        HttpResponse.json(
          {
            status: 401,
            title: "Não foi possível entrar",
            detail: "Usuário ou senha incorretos.",
          },
          {
            status: 401,
            headers: { "Content-Type": "application/problem+json" },
          }
        )
      )
    )
    const user = userEvent.setup()
    renderWithProviders(<LoginForm onSuccess={vi.fn()} canRegister={false} />)

    await user.type(screen.getByLabelText("@usuario"), "ana")
    await user.type(screen.getByLabelText("Senha"), "senha errada qualquer")
    await user.click(screen.getByRole("button", { name: "Entrar" }))

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Usuário ou senha incorretos."
    )
    expect(screen.getByLabelText("@usuario")).toHaveValue("ana")
  })

  it("pede o código TOTP quando o servidor responde totpRequired e envia junto no segundo passo", async () => {
    const bodies: unknown[] = []
    server.use(
      http.post("/api/v1/auth/login", async ({ request }) => {
        const body = (await request.json()) as { totpCode?: string }
        bodies.push(body)
        if (!body.totpCode) {
          return HttpResponse.json(
            { status: 401, title: "Código", totpRequired: true },
            {
              status: 401,
              headers: { "Content-Type": "application/problem+json" },
            }
          )
        }
        return HttpResponse.json(me)
      })
    )
    const onSuccess = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<LoginForm onSuccess={onSuccess} canRegister={false} />)

    await user.type(screen.getByLabelText("@usuario"), "ana")
    await user.type(screen.getByLabelText("Senha"), "senha muito segura 123")
    await user.click(screen.getByRole("button", { name: "Entrar" }))
    const code = await screen.findByLabelText("Código de verificação")
    await user.type(code, "123456")
    await user.click(screen.getByRole("button", { name: "Entrar" }))

    await vi.waitFor(() => expect(onSuccess).toHaveBeenCalled())
    expect(bodies.at(-1)).toMatchObject({ username: "ana", totpCode: "123456" })
  })
})
