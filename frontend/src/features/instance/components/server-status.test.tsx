import { describe, expect, it } from "vitest"
import { http, HttpResponse } from "msw"
import { screen } from "@testing-library/react"

import { server } from "@/test/msw/server"
import { renderWithProviders } from "@/test/test-utils"
import { ServerStatus } from "./server-status"

describe("ServerStatus", () => {
  it("mostra servidor disponível quando o health responde UP", async () => {
    renderWithProviders(<ServerStatus />)
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Servidor disponível"
    )
  })

  it("mostra servidor indisponível quando o health falha", async () => {
    server.use(
      http.get("/actuator/health", () =>
        HttpResponse.json({ status: "DOWN" }, { status: 503 })
      )
    )
    renderWithProviders(<ServerStatus />)
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Servidor indisponível"
    )
  })
})
