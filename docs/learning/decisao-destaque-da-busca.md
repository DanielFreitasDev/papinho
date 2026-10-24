---
name: Destaque da busca pela mesma regra do servidor
description: o cliente marca o termo com um Snowball português igual ao portuguese_stem, provado contra o PostgreSQL, e leva o termo pelo estado do histórico
type: project
category: decision
date: 2026-10-24
---
O destaque do termo buscado (GR-06) segue exatamente a regra do `MessageSearch`: o cliente tem um porte do Snowball
português (`format/stem-pt.ts`), a mesma normalização (`format/search-match.ts`) e uma fixture com o radical que o próprio
PostgreSQL devolve para 5.294 palavras, conferida dos dois lados.

**Problema/Contexto:** a busca do servidor acha por radical (`papinho_portuguese` = `unaccent` + `portuguese_stem`,
com os plurais -ões/-ães/-ãos/-ns trocados em `papinho_search_text`) e pelo trecho com `unaccent(lower(body)) like`.
Destacar só a substring digitada deixaria sem marca boa parte do que a busca acha ("reuniões" para "reunião",
"falando" para "falar"). Detalhes que a fixture revelou: o `unaccent` roda antes do stemmer, então as regras acentuadas
do Snowball nunca valem e só as palavras vazias sem acento contam ("não" vira "nao", que é indexado); o `unaccent` troca
ª/º, ß, æ e ligaduras, mas não ²/³, e o analisador separa "x²" em "x".
**Por que importa:** com outra regra, o marca-texto discorda dos resultados. Mudou a configuração da busca no banco?
O `SearchIT.shouldAgreeWithTheLexemesTheClientHighlights` falha até a fixture e o stemmer mudarem juntos (a fixture é
regenerada com `to_tsvector('papinho_portuguese', papinho_search_text(palavra))`).
**Como aplicar:** destaque sempre por `searchTerms`/`findMatches`/`highlightText` (posições mapeadas para o texto
original, uma por unidade UTF-16 por causa dos emojis); nunca HTML. Spoiler fechado não recebe marca (o amarelo
mostraria onde está o termo). O termo da busca global vai para a conversa pelo estado do histórico (`HistoryState.
searchQuery`), não pela URL, e é o que está escrito na caixa (o resultado clicado pode ser da busca anterior à espera
da digitação, como o `organize.spec.ts` mostrou). Lint do React Compiler: função usada antes da declaração
(`react-hooks/immutability`) e `setState` direto num efeito (`react-hooks/set-state-in-effect`) são erro; declare antes
e ajuste o estado durante a renderização quando uma prop muda.
**Evidência:** `stem-pt.test.ts` (5.292/5.294 no primeiro rascunho, 5.294/5.294 depois de tratar ª e "x²"),
`search-match.test.ts` (o mapa de posições errava com emoji antes da correção), `SearchIT` 6/6, `search.spec.ts` e
`organize.spec.ts` nos dois projetos; 2026-10-24.
