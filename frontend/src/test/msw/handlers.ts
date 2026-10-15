import { http, HttpResponse } from "msw"

export const handlers = [
  http.get("/actuator/health", () => HttpResponse.json({ status: "UP" })),
]
