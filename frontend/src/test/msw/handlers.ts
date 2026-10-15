import { http, HttpResponse } from "msw"

export const handlers = [
  http.get("/actuator/health", () => HttpResponse.json({ status: "UP" })),
  // O apiFetch busca o cookie CSRF antes da primeira escrita.
  http.get("/api/v1/instance", () =>
    HttpResponse.json({
      name: "Papinho",
      registrationMode: "INVITE_ONLY",
      setupRequired: false,
    })
  ),
]
