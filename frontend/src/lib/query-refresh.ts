import { hashKey, type QueryClient, type QueryKey } from "@tanstack/react-query"

type Round = { again: boolean; promise: Promise<void> }

const rounds = new Map<string, Round>()

/**
 * Recarrega as consultas da chave depois de um aviso do servidor, sem cancelar a busca em andamento.
 *
 * O invalidateQueries padrão cancela a busca que está rodando e começa outra. Com HTTP/2 (perfil lan, PK-02) cada
 * cancelamento vira um RST_STREAM, e uma rajada de avisos (várias mensagens e confirmações seguidas) fazia o Tomcat
 * derrubar a conexão com ENHANCE_YOUR_CALM, a proteção contra "rapid reset". Aqui há uma rodada por vez por chave:
 * espera o que já está em andamento (pode ter saído antes da mudança avisada), busca de novo e, se outro aviso
 * chegou nesse meio-tempo, faz mais uma rodada no fim.
 */
export function refreshQueries(
  queryClient: QueryClient,
  queryKey: QueryKey
): Promise<void> {
  const id = hashKey(queryKey)
  const running = rounds.get(id)
  if (running) {
    running.again = true
    return running.promise
  }
  const round: Round = { again: true, promise: Promise.resolve() }
  rounds.set(id, round)
  round.promise = (async () => {
    while (round.again) {
      round.again = false
      await settled(queryClient, queryKey)
      await queryClient
        .invalidateQueries({ queryKey }, { cancelRefetch: false })
        .catch(() => undefined)
    }
  })().finally(() => rounds.delete(id))
  return round.promise
}

/** Resolve quando nenhuma consulta da chave está buscando. */
function settled(queryClient: QueryClient, queryKey: QueryKey) {
  if (queryClient.isFetching({ queryKey }) === 0) {
    return Promise.resolve()
  }
  return new Promise<void>((resolve) => {
    const unsubscribe = queryClient.getQueryCache().subscribe(() => {
      if (queryClient.isFetching({ queryKey }) === 0) {
        unsubscribe()
        resolve()
      }
    })
  })
}
