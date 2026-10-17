import Dexie, { type Table } from "dexie"

/**
 * Dados offline no IndexedDB (NT-05), num banco por conta ("papinho-<id>"): o último estado das conversas
 * recentes e a fila de textos ainda não enviados. O banco de uma conta é apagado no logout e quando outra conta
 * entra neste navegador; conversas trancadas (PV-03) nunca são guardadas. Respostas da API não vão para o Cache
 * Storage: só o que o próprio app escolhe guardar aqui.
 */
export type Snapshot = { key: string; value: unknown; updatedAt: number }

/** Texto escrito sem conexão, reenviado com o mesmo clientMessageId (RT-02) quando a conexão volta. */
export type OutboxItem = {
  clientMessageId: string
  conversationId: string
  body: string
  replyToId: string | null
  silent: boolean
  createdAt: string
}

class OfflineDb extends Dexie {
  snapshots!: Table<Snapshot, string>
  outbox!: Table<OutboxItem, string>

  constructor(name: string) {
    super(name)
    this.version(1).stores({
      snapshots: "key",
      outbox: "clientMessageId, conversationId, createdAt",
    })
  }
}

const PREFIX = "papinho-"
const LAST_USER = "papinho.lastUser"

let current: { userId: string; db: OfflineDb } | null = null
let opening: { userId: string; ready: Promise<OfflineDb> } | null = null

/** Abre (uma vez só) o banco da conta e apaga os de outras contas deste navegador. */
export function openOfflineDb(userId: string) {
  if (opening?.userId === userId) {
    return opening.ready
  }
  current?.db.close()
  const db = new OfflineDb(PREFIX + userId)
  current = { userId, db }
  try {
    localStorage.setItem(LAST_USER, userId)
  } catch {
    // Sem localStorage (janela privada restrita): só não abre offline na próxima vez.
  }
  const ready = (async () => {
    const names = await Dexie.getDatabaseNames().catch(() => [] as string[])
    await Promise.all(
      names
        .filter((name) => name.startsWith(PREFIX) && name !== PREFIX + userId)
        .map((name) => Dexie.delete(name).catch(() => undefined))
    )
    return db
  })()
  opening = { userId, ready }
  return ready
}

/** Banco da conta aberta ou, antes de o app saber quem é, o da última conta deste navegador. */
async function database() {
  if (current) {
    return current.db
  }
  const userId = lastUserId()
  return userId ? openOfflineDb(userId) : null
}

export function offlineDb() {
  return current?.db ?? null
}

/** Conta da última sessão neste navegador, para abrir os dados offline sem rede. */
export function lastUserId() {
  try {
    return localStorage.getItem(LAST_USER)
  } catch {
    return null
  }
}

/** Logout: apaga tudo o que a conta deixou neste navegador. */
export async function clearOfflineData() {
  const userId = current?.userId ?? lastUserId()
  current?.db.close()
  current = null
  opening = null
  try {
    localStorage.removeItem(LAST_USER)
  } catch {
    // ignora
  }
  if (userId) {
    await Dexie.delete(PREFIX + userId)
  }
}

export async function readSnapshot<T>(key: string) {
  const db = await database()
  const found = await db?.snapshots.get(key).catch(() => undefined)
  return found?.value as T | undefined
}

export async function writeSnapshot(key: string, value: unknown) {
  await current?.db.snapshots
    .put({ key, value, updatedAt: Date.now() })
    .catch(() => undefined)
}

export async function deleteSnapshot(key: string) {
  await current?.db.snapshots.delete(key).catch(() => undefined)
}

/** Apaga do aparelho tudo de uma conversa: dados, mensagens e textos na fila (conversa trancada, PV-03). */
export async function forgetConversation(conversationId: string) {
  const db = current?.db
  if (!db) {
    return
  }
  await db
    .transaction("rw", db.snapshots, db.outbox, async () => {
      await db.snapshots.bulkDelete([
        `detail:${conversationId}`,
        `messages:${conversationId}`,
      ])
      await db.outbox.where("conversationId").equals(conversationId).delete()
    })
    .catch(() => undefined)
}

/** Guarda na fila um texto que ainda não foi confirmado pelo servidor. */
export async function enqueue(item: OutboxItem) {
  await current?.db.outbox.put(item).catch(() => undefined)
}

export async function dequeue(clientMessageId: string) {
  await current?.db.outbox.delete(clientMessageId).catch(() => undefined)
}

/**
 * Rede primeiro; só sem rede (não numa resposta de erro do servidor) usa o que ficou guardado (NT-05). Assim
 * nada antigo do aparelho aparece por cima do estado real quando o servidor responde.
 */
export async function networkFirst<T>(
  key: string,
  load: () => Promise<T>,
  isServerError: (error: unknown) => boolean
): Promise<T> {
  try {
    return await load()
  } catch (error) {
    const aborted = error instanceof DOMException && error.name === "AbortError"
    if (aborted || isServerError(error)) {
      throw error
    }
    const stored = await readSnapshot<T>(key)
    if (stored === undefined) {
      throw error
    }
    return stored
  }
}

/** Textos da fila de uma conversa, em ordem. */
export async function queuedFor(conversationId: string) {
  const db = await database()
  if (!db) {
    return []
  }
  return db.outbox
    .where("conversationId")
    .equals(conversationId)
    .sortBy("createdAt")
    .catch(() => [] as OutboxItem[])
}
