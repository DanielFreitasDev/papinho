/**
 * Cliente HTTP do SPA. Mesma origem que o backend (ADR 0002): o cookie de sessão
 * vai sozinho e o token CSRF é lido do cookie XSRF-TOKEN (csrf.spa() no servidor).
 */

export type ProblemDetail = {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  [key: string]: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`)
    this.name = "ApiError"
    this.status = problem.status
    this.problem = problem
  }
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"])

export function readCookie(name: string): string | undefined {
  return document.cookie
    .split("; ")
    .find((part) => part.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit & { json?: unknown } = {}
): Promise<T> {
  const { json, headers, ...rest } = init
  const method = (rest.method ?? "GET").toUpperCase()
  const finalHeaders = new Headers(headers)
  finalHeaders.set("Accept", "application/json, application/problem+json")
  if (json !== undefined) {
    finalHeaders.set("Content-Type", "application/json")
  }
  if (!SAFE_METHODS.has(method)) {
    const token = readCookie("XSRF-TOKEN")
    if (token) {
      finalHeaders.set("X-XSRF-TOKEN", decodeURIComponent(token))
    }
  }

  const response = await fetch(path, {
    ...rest,
    method,
    headers: finalHeaders,
    credentials: "same-origin",
    body: json !== undefined ? JSON.stringify(json) : rest.body,
  })

  if (!response.ok) {
    let problem: ProblemDetail = { status: response.status }
    const type = response.headers.get("Content-Type") ?? ""
    if (type.includes("json")) {
      problem = { ...(await response.json()), status: response.status }
    }
    throw new ApiError(problem)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
