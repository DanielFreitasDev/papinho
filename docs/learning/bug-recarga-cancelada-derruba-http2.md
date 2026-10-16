---
name: Recargas canceladas em rajada derrubavam a conexão HTTP/2
description: invalidateQueries cancela a busca em andamento; no HTTP/2 cada cancelamento é RST_STREAM e o Tomcat responde ENHANCE_YOUR_CALM
type: project
category: bug
date: 2026-10-16
---
Nunca recarregue a lista de conversas com `invalidateQueries` a cada aviso: use `refreshQueries`
(`src/lib/query-refresh.ts`), que espera a busca em andamento, busca de novo sem cancelar e faz no máximo mais uma
rodada se outro aviso chegou no meio.

**Problema/Contexto:** cada aviso do tempo real (mensagem, confirmação) e cada ação (enviar, marcar como lida)
chamava `queryClient.invalidateQueries({ queryKey: ["conversations"] })`. O TanStack Query v5 cancela a busca em
andamento de uma consulta **que já tem dados** e começa outra (sem dados, ele só se junta à busca). Numa rajada de
mensagens, dezenas de buscas eram abortadas. No perfil lan (HTTPS com HTTP/2, PK-02) cada aborto é um `RST_STREAM`;
o Tomcat trata a rajada como ataque "rapid reset" e fecha a conexão com `ENHANCE_YOUR_CALM` (no navegador:
"Failed to load resource: HTTP/2 Error: ENHANCE_YOUR_CALM" e requisições sem resposta). No CI o e2e roda em HTTP/1.1
(porta 8080), por isso nunca apareceu.

**Por que importa:** quem usa o Papinho na LAN, onde o HTTP/2 é o normal, perdia mensagens e ações em conversas
movimentadas.

**Como aplicar:** recargas disparadas por evento usam `refreshQueries`; `invalidateQueries` fica para ações isoladas
de chaves que não recebem rajadas. Vale para qualquer recarga em resposta a eventos do servidor.

**Evidência:** trace do WebKit com dezenas de `GET /api/v1/conversations` "Load request cancelled" seguidos de
ENHANCE_YOUR_CALM (2026-10-16); `query-refresh.test.ts` mostra 4 de 6 buscas abortadas numa rajada de 5 avisos com
o padrão antigo e nenhuma com `refreshQueries`; depois da correção, Chromium e Firefox enviam 13 de 13 mensagens
pelo HTTP/2 sem nenhum cancelamento. Commit `ec39b78`.
