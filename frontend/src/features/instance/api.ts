import { queryOptions } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"

export type HealthResponse = { status: "UP" | "DOWN" | string }

export const instanceQueries = {
  health: () =>
    queryOptions({
      queryKey: ["instance", "health"],
      queryFn: ({ signal }) =>
        apiFetch<HealthResponse>("/actuator/health", { signal }),
      retry: false,
    }),
}
