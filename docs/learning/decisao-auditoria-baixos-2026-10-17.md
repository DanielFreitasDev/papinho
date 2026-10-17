---
name: Decisões da correção dos achados Baixos (2026-10-17)
description: limite de login por IP e conta, grant dos anexos trancados, reservas de conta apagada, antivírus fora e outras
type: project
category: decision
date: 2026-10-17
---
- **Login (A43):** o balde de 10 erros é por par IP e conta, e a conta tem um teto de 50 somando os IPs: quem chuta a
  senha de outra pessoa trava o próprio endereço, e a força bruta distribuída ainda para. A recuperação usa os mesmos.
- **Recuperação em tempo constante (A44):** sempre `RECOVERY_CODE_COUNT` hashes, sem parar no acerto.
- **Conta apagada (A46):** o `@usuario` atual e os antigos ficam reservados, sem dono (`ON DELETE SET NULL`), até o fim
  dos 30 dias; o id dela sai dos avisos de grupo (vira o UUID nulo) e da chave das conversas individuais (A49).
- **Anexos de conversa trancada (A70, ADR 0013):** grant HMAC por pessoa e arquivo, chave só na memória, 1 hora, na URL
  (`?grant=`), vindo nas mensagens servidas com o token. Cookie foi descartado (não dá para limitar por aba).
- **Antivírus fora do ar (A59):** 503 e o envio descartado; sem análise nada é aceito.
- **Editar apagada (A53):** 409 `message-not-editable`; apagar de novo continua idempotente (200).
- **Bloqueio (A56):** votar e reagir contam como interagir; em grupo o bloqueio não impede, como no envio.
- **Provedor de transcrição (A62):** mudar a origem (esquema, host ou porta) sem informar a chave apaga a chave.
- **NUL em texto (A78):** o SQLState 22021 do PostgreSQL vira 400 `invalid-text` no handler, em vez de filtrar cada
  campo.
- **HSTS (A79):** continua opcional (`PAPINHO_HSTS`); ligar sozinho no HTTPS prenderia o navegador à CA da casa no lan.
- **Busca (A65):** plurais normalizados antes do stemmer; um dicionário ispell pt_BR ficou de fora (não vem no
  contrib do contêiner e exigiria instalar arquivos no Postgres).
- **onnxruntime-web dev (A92, ADR 0008):** fica o build que o Transformers.js fixa; um teste compara as versões.
- **Assunto dos commits (A93):** hook `commit-msg` opcional e job do CI só nos commits novos; o histórico enviado não
  é reescrito.

**Evidência:** commits dos achados A43 a A96 desta passada (tabela no STATUS e no fim da passada).

**Aplicação:** quem mexer em login, recuperação, exclusão de conta, conversas trancadas, uploads ou busca.
