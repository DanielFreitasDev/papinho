---
name: unaccent troca formas largas por curingas do LIKE
description: o unaccent transforma ％ ＿ ＼ em % _ \, então escapar o LIKE antes dele não protege; starts_with não tem curingas; [:punct:] do PostgreSQL inclui emoji
type: project
category: gotcha
date: 2026-10-28
---
Escapar `%`, `_` e `\` no Java e só depois passar o padrão pelo `papinho_unaccent` não basta: o dicionário do
`unaccent` troca as formas largas pelos próprios curingas. Para "começa com", use `starts_with(texto, prefixo)`, que
compara texto puro e dispensa o escape.

**Problema/Contexto:** o `unaccent.rules` do PostgreSQL 18 tem `％ → %`, `﹪ → %`, `＿ → _`, `＼ → \` e `∖ → \`, e
algumas regras devolvem espaço (`½ → " 1/2"`). No `postgres:18-alpine` (`en_US.utf8`, provedor libc), as classes do
regex valem para qualquer alfabeto: `[:alnum:]` aceita Ж e 名, e `[:punct:]` inclui emoji e símbolos (😀, €, ©), além
de `% _ \`. Quebrar em `[[:space:][:punct:]]+` pode deixar `''` nas pontas (tire com `array_remove`).
**Por que importa:** com o padrão escapado antes do unaccent, buscar "％" vira `%%%` e casa qualquer texto. O
`MessageSearch` (busca de mensagens) monta o padrão assim: o efeito fica nas conversas de quem busca (resultado
errado, não vazamento), mas o mesmo jeito numa busca de pessoas ou num filtro de acesso seria pior.
**Como aplicar:** compare prefixos com `starts_with` (busca de pessoas: `ProfileSearch` e
`UserAccountRepository.findEnabledByUsernamePrefix`). Precisando de `LIKE` com trecho do usuário, escape depois do
unaccent, no próprio SQL (`replace(papinho_unaccent(:q), '\', '\\')` e assim por diante), ou quebre o texto em
palavras na pontuação, como o `ProfileSearch` faz, para que nenhum curinga sobre.
**Evidência:** consultas num contêiner `postgres:18-alpine` descartável (2026-10-28):
`papinho_unaccent('mensagem qualquer') like papinho_unaccent('%' || '％' || '%') escape '\'` deu `t` (com `\%`, `f`);
`'😀' ~ '[[:punct:]]'` e `'Ж' ~ '[[:alnum:]]'` deram `t`. `PeopleSearchIT.shouldTreatLikeWildcardsAsText` ("％％",
"%%", "__" e "d_n" sem resultado).
