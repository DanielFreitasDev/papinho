---
name: Limpeza dos arquivos enviados e nunca usados
description: job de 24 h com porta MediaReferences e marca in_use preguiçosa, sem a mídia depender das mensagens
type: project
category: decision
date: 2026-10-16
---
Arquivo enviado (tus) que não vira anexo, figurinha nem foto de grupo em 24 h sai do servidor e devolve a cota
(MD-01). Antes ficava para sempre no disco e na cota de quem enviou.

**Decisão:** o job `UnusedFileCleanupJob` (ShedLock, a cada hora, `app.media.unused-file-expiry`) trava em lotes os
arquivos antigos ainda não conferidos (`for update skip locked`) e pergunta às implementações da porta
`MediaReferences` quais estão em uso. As mensagens implementam a porta (anexos, figurinhas, fotos de grupo): a
mídia não depende delas, como em `MediaAccessPolicy`. Os usados ganham `in_use = true` e não voltam a ser
conferidos (a primeira rodada numa instância antiga confere tudo uma vez); os outros são apagados, com o conteúdo
removido depois do commit.
**Alternativas descartadas:** marcar o uso em cada caminho que anexa (exigiria mexer em mensagens, figurinhas,
grupos, GIFs e um backfill que consulta tabelas de outro módulo); conferir todos os arquivos a cada rodada (custo
crescente); depender só das FKs (a de `group_details` é `ON DELETE SET NULL` e apagaria fotos de grupo).
**Concorrência:** o anexo novo pede `FOR KEY SHARE` no arquivo pela FK; com a trava `FOR UPDATE` da limpeza ele
espera e falha pela FK em vez de apontar para um arquivo apagado; um arquivo já travado por um anexo em andamento
fica para a próxima rodada.
**Limites:** sem nenhuma implementação da porta, a limpeza não apaga nada. Um caminho novo que guarde referência a
`media_files` precisa entrar em `MessageMediaReferences` (ou numa porta própria do módulo).
**Evidência:** `MediaIT.shouldPurgeFilesNeverUsed` (cota cheia, arquivo esquecido apagado do banco e do
armazenamento depois de 24 h, cota devolvida; anexo, figurinha, foto de grupo e arquivo recente ficam; os usados
conferidos uma vez só), commit `563ec62`.
