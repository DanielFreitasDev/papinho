import { QueryClient } from "@tanstack/react-query"

import { ApiError } from "@/lib/api-client"

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Erros 4xx não melhoram com nova tentativa; só falhas de rede e 5xx.
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status < 500) && failureCount < 2,
    },
  },
})
