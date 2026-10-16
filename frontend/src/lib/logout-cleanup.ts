/**
 * Limpezas que precisam rodar antes de sair da conta, ainda com a sessão válida: tirar a assinatura de push
 * deste navegador (NT-01) e apagar os dados offline da pessoa (NT-05). Cada feature registra a sua; uma
 * falha não impede o logout.
 */
const cleanups = new Set<() => Promise<void>>()

export function onLogout(cleanup: () => Promise<void>) {
  cleanups.add(cleanup)
  return () => cleanups.delete(cleanup)
}

export async function runLogoutCleanups() {
  await Promise.all(
    [...cleanups].map((cleanup) => cleanup().catch(() => undefined))
  )
}
