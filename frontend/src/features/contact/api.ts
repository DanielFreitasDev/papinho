import {
  queryOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"

import { apiFetch } from "@/lib/api-client"
import type { Blocked, Contact, MyLink, Person } from "./types"

export const contactKeys = {
  all: ["contacts"] as const,
  list: ["contacts", "list"] as const,
  person: (username: string) => ["contacts", "person", username] as const,
  search: (query: string) => ["contacts", "search", query] as const,
  myLink: ["contacts", "my-link"] as const,
  blocks: ["contacts", "blocks"] as const,
}

export const contactQueries = {
  list: () =>
    queryOptions({
      queryKey: contactKeys.list,
      queryFn: ({ signal }) =>
        apiFetch<Contact[]>("/api/v1/contacts", { signal }),
    }),
  person: (username: string) =>
    queryOptions({
      queryKey: contactKeys.person(username),
      queryFn: ({ signal }) =>
        apiFetch<Person>(`/api/v1/people/${encodeURIComponent(username)}`, {
          signal,
        }),
      retry: false,
    }),
  /** Pessoas da instância pelo nome ou pelo @usuario (o servidor aplica bloqueio e "aparecer na busca"). */
  search: (query: string) =>
    queryOptions({
      queryKey: contactKeys.search(query),
      queryFn: ({ signal }) =>
        apiFetch<Person[]>(`/api/v1/people?q=${encodeURIComponent(query)}`, {
          signal,
        }),
      // A lista anterior fica na tela enquanto a nova chega: nada pisca a cada letra.
      placeholderData: (previous) => previous,
      staleTime: 30_000,
    }),
  myLink: () =>
    queryOptions({
      queryKey: contactKeys.myLink,
      queryFn: ({ signal }) =>
        apiFetch<MyLink>("/api/v1/contacts/my-link", { signal }),
      staleTime: Infinity,
    }),
  blocks: () =>
    queryOptions({
      queryKey: contactKeys.blocks,
      queryFn: ({ signal }) =>
        apiFetch<Blocked[]>("/api/v1/blocks", { signal }),
    }),
}

function useInvalidateContacts() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: contactKeys.all })
}

export function useAddContact() {
  const invalidate = useInvalidateContacts()
  return useMutation({
    mutationFn: (input: { username: string; nickname?: string }) =>
      apiFetch<Contact>("/api/v1/contacts", { method: "POST", json: input }),
    onSuccess: invalidate,
  })
}

export function useRenameContact() {
  const invalidate = useInvalidateContacts()
  return useMutation({
    mutationFn: (input: { userId: string; nickname: string | null }) =>
      apiFetch<Contact>(`/api/v1/contacts/${input.userId}`, {
        method: "PATCH",
        json: { nickname: input.nickname },
      }),
    onSuccess: invalidate,
  })
}

export function useRemoveContact() {
  const invalidate = useInvalidateContacts()
  return useMutation({
    mutationFn: (userId: string) =>
      apiFetch<void>(`/api/v1/contacts/${userId}`, { method: "DELETE" }),
    onSuccess: invalidate,
  })
}

export function useBlock() {
  const invalidate = useInvalidateContacts()
  return useMutation({
    mutationFn: ({ userId, blocked }: { userId: string; blocked: boolean }) =>
      apiFetch<void>(`/api/v1/blocks/${userId}`, {
        method: blocked ? "PUT" : "DELETE",
      }),
    onSuccess: invalidate,
  })
}
