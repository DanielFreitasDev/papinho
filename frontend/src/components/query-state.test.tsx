import { describe, expect, it, vi } from "vitest"
import { fireEvent, render, screen } from "@testing-library/react"

import { ApiError } from "@/lib/api-client"
import { QueryState, queryProblem } from "./query-state"

type Query = Parameters<typeof QueryState<string[]>>[0]["query"]

function query(overrides: Partial<Query>): Query {
  return {
    data: undefined,
    error: null,
    isPending: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
    ...overrides,
  } as Query
}

const forbidden = new ApiError({ status: 403, title: "Acesso negado" })
const serverError = new ApiError({
  status: 500,
  title: "Erro interno",
  detail: "Detalhe do servidor",
})

describe("QueryState", () => {
  it("mostra o carregamento", () => {
    render(
      <QueryState query={query({ isPending: true })}>
        {() => "dados"}
      </QueryState>
    )
    expect(screen.getByRole("status")).toHaveTextContent("Carregando…")
  })

  it("mostra o vazio quando a lista não tem itens", () => {
    render(
      <QueryState
        query={query({ data: [] })}
        isEmpty={(list) => list.length === 0}
        empty={{ title: "Nada aqui" }}
      >
        {() => "dados"}
      </QueryState>
    )
    expect(screen.getByText("Nada aqui")).toBeInTheDocument()
    expect(screen.queryByText("dados")).not.toBeInTheDocument()
  })

  it("entrega os dados quando existem", () => {
    render(
      <QueryState query={query({ data: ["a", "b"] })}>
        {(list) => list.join(",")}
      </QueryState>
    )
    expect(screen.getByText("a,b")).toBeInTheDocument()
  })

  it("erro do servidor mostra o detalhe e tenta de novo", () => {
    const refetch = vi.fn()
    render(
      <QueryState query={query({ isError: true, error: serverError, refetch })}>
        {() => "dados"}
      </QueryState>
    )
    expect(screen.getByRole("alert")).toHaveTextContent("Detalhe do servidor")
    fireEvent.click(screen.getByRole("button", { name: "Tentar de novo" }))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it("403 vira sem permissão, sem botão de tentar de novo", () => {
    render(
      <QueryState query={query({ isError: true, error: forbidden })}>
        {() => "dados"}
      </QueryState>
    )
    expect(screen.getByRole("alert")).toHaveTextContent("Sem permissão")
    expect(screen.queryByRole("button")).not.toBeInTheDocument()
  })

  it("falha sem resposta do servidor vira sem conexão", () => {
    expect(queryProblem(new TypeError("Failed to fetch"))).toBe("offline")
    render(
      <QueryState
        query={query({
          isError: true,
          error: new TypeError("Failed to fetch"),
        })}
      >
        {() => "dados"}
      </QueryState>
    )
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Sem conexão com o servidor"
    )
  })
})
