---
name: Invalidar uma lista que ainda está na primeira busca não traz a mudança
description: no TanStack Query, invalidateQueries sem dados reaproveita a busca em andamento; use refreshQueries depois de mutações
type: project
category: bug
date: 2026-10-16
---
No TanStack Query 5, `invalidateQueries` numa consulta que ainda não tem dados (primeira busca em andamento) não
cancela nem repete a busca: ela reaproveita a promessa que já está em voo, que saiu antes da mudança. Ao terminar, a
consulta fica fresca (`staleTime` de 30 s no app) e a marca de invalidada some.

**Problema/Contexto:** o gerenciador de figurinhas busca os pacotes ao abrir; quem criava um pacote antes de a lista
chegar (rede lenta, ou o e2e no celular digitando rápido) não via o pacote novo até reabrir. O `groups.spec.ts` falhou
assim no celular: o trace mostra o GET dos pacotes saindo antes do POST e nenhum GET depois dele. O mesmo valia para
criar convite com o diálogo recém-aberto.
**Por que importa:** a ação dá certo no servidor, mas a tela diz o contrário; parece bug intermitente.
**Como aplicar:** depois de uma mutação cuja lista pode ainda estar carregando, use `refreshQueries` (`src/lib/
query-refresh.ts`): espera a busca em andamento, busca de novo e não cancela nada (sem RST_STREAM no HTTP/2). Sem
`await` quando a tela não deve esperar a recarga (o link do convite aparece na hora).
**Evidência:** `src/features/sticker/api.test.tsx` (com a primeira busca em voo, cria o pacote: sem a correção a
lista fica `[]`, com ela traz o pacote e faz dois GET); `groups.spec.ts` 6/6 depois (2026-10-16).
