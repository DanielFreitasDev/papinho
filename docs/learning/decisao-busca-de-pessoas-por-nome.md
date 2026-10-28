---
name: Busca de pessoas por nome no "Adicionar contato"
description: decisão do dono — todos aparecem por padrão e cada um pode sair em Privacidade; quem saiu ainda aparece pelo @usuario exato e para os contatos; bloqueio nunca; sem apelido no diálogo
type: project
category: decision
date: 2026-10-28
---
O "Adicionar contato" busca as pessoas da instância enquanto se digita, pelo nome ou pelo começo do `@usuario`, como
a busca de perfis do Facebook e do Instagram. A privacidade foi decidida pelo dono.

**Problema/Contexto:** o pedido (2026-10-28) foi "ao digitar, já sugerir… você busca pelo nome e adiciona para
começar uma conversa". Até então só dava para adicionar pelo `@usuario` exato, link ou QR Code (CT-01), e uma busca
por nome deixa qualquer pessoa da instância listar as outras.
**Decisão:** o dono escolheu "todos aparecem, com opção de sair" entre três opções (todos sem opção; só quem ativar).
- `privacy_settings.name_search` nasce ligado (`V202610280100`), então ninguém sumiu da busca na instância em uso.
- Quem desliga "Aparecer na busca por nome" continua aparecendo pelo `@usuario` exato (que o link e o QR Code já
  achavam) e para quem já o tem nos contatos (não revela nada novo).
- Bloqueio em qualquer direção, contas desativadas e a própria pessoa nunca aparecem. Foto e recado passam pelo
  perfil, então o PV-04 vale nos resultados.
- A busca tem 2 a 64 caracteres, até 20 resultados e 120 buscas por minuto por pessoa (contra varrer a lista).
- O diálogo perdeu o campo de apelido: o apelido local (CT-01) continua em "Mudar apelido" na lista de contatos.
- "Adicionar" deixa a pessoa nos contatos e troca o botão por "Conversar", que abre a conversa; dá para adicionar
  várias pessoas sem fechar o diálogo.
**Como aplicar:** regra nova de visibilidade de pessoas (sugestões em grupos, menções) deve passar pela mesma porta
`NameSearchPolicy` e pelo bloqueio. A busca percorre a tabela de perfis a cada consulta; numa instância muito grande,
precisaria de índice (não medido).
**Evidência:** `PeopleSearchIT` (11 casos, 15 mutações derrubadas), `PrivacyIT.shouldToggleNameSearch`,
`ContactServiceTest`; e2e `people-search.spec.ts` (sem acento e por pedaços do nome, opção desligada some do nome e
aparece pelo `@usuario`, "Conversar" abre a conversa); a escolha do dono na pergunta desta sessão (2026-10-28).
