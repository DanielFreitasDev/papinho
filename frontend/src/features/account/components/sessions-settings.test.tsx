import { describe, expect, it, vi } from "vitest"
import { http, HttpResponse } from "msw"
import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"

import { toast } from "@/components/ui/toast"
import { server } from "@/test/msw/server"
import { renderWithProviders } from "@/test/test-utils"
import { SessionsSettings } from "./sessions-settings"

vi.mock("@/components/ui/toast", () => ({ toast: { add: vi.fn() } }))

const sessions = [
  {
    id: "atual",
    device: "Firefox no Linux",
    approximateIp: "192.168.0.x",
    createdAt: "2026-10-16T12:00:00Z",
    lastAccessedAt: "2026-10-16T12:00:00Z",
    current: true,
  },
  {
    id: "celular",
    device: "Chrome no Android",
    approximateIp: "192.168.0.x",
    createdAt: "2026-10-16T11:00:00Z",
    lastAccessedAt: "2026-10-16T11:30:00Z",
    current: false,
  },
]

describe("SessionsSettings", () => {
  it("avisa quando não consegue encerrar uma sessão, em vez de falhar em silêncio", async () => {
    server.use(
      http.get("/api/v1/sessions", () => HttpResponse.json(sessions)),
      http.delete("/api/v1/sessions/celular", () =>
        HttpResponse.json(
          {
            status: 404,
            title: "Não encontrado",
            detail: "Sessão não encontrada.",
          },
          {
            status: 404,
            headers: { "Content-Type": "application/problem+json" },
          }
        )
      )
    )
    const user = userEvent.setup()
    renderWithProviders(<SessionsSettings />)

    await user.click(await screen.findByRole("button", { name: "Encerrar" }))

    await vi.waitFor(() =>
      expect(toast.add).toHaveBeenCalledWith({
        title: "Sessão não encontrada.",
        type: "error",
      })
    )
    expect(toast.add).not.toHaveBeenCalledWith({ title: "Sessão encerrada" })
  })
})
