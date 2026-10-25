---
name: Repositório público e o runner self-hosted da VPS
description: CI parado pela cota de minutos que os repositórios privados da conta dividem; repositório aberto com aprovação de todo PR de fora, varredura de segredos antes e o risco que sobra no runner com sudo
type: project
category: decision
date: 2026-10-25
---
**Por que abriu:** em repositório privado, os minutos do Actions saem de uma cota mensal da conta inteira (plano
Free: 2.000 min e 500 MB de artifacts, segundo a documentação de cobrança do GitHub), dividida com os outros
repositórios privados do dono. Em 2026-10-24 a cota acabou e o GitHub passou a recusar os jobs com "recent account
payments have failed or your spending limit needs to be increased". Não é cobrança: sem forma de pagamento, "usage is
blocked once you use up your quota"; a visão geral mostra o uso medido igual ao desconto incluído e nenhum pagamento
previsto, e os minutos voltam no início do ciclo seguinte. O dono decidiu abrir o repositório (2026-10-25): em
repositório público, os runners padrão são grátis e sem limite (e têm 4 vCPUs, contra 2 no privado).

**Antes de abrir:** gitleaks v8.28.0 (imagem Docker, modo `git` com `--redact`) nos 353 commits: 4 achados
`generic-api-key`, todos falsos positivos (token de teste, alfabeto de senhas, rótulo PEM). Nenhum `.env`, chave ou
arquivo da PKI no histórico, e o IP da VPS não aparece. O e-mail de autoria dos commits do dono ficou público.

**Runner self-hosted:** um PR de fork pode mudar o workflow e mandar um job para `[self-hosted, papinho]`. Os runners
da VPS rodam como `ubuntu`, que tem sudo sem senha para tudo (regra da imagem de nuvem, além das regras de cada
`deploy.sh`), numa máquina que também hospeda outros serviços. Controle: "Require approval for all external
contributors" (`PUT .../actions/permissions/fork-pr-contributor-approval` com `approval_policy=all_external_contributors`;
com o repositório ainda privado, a API responde 422, então vai logo depois de abrir). Nenhum workflow de PR de quem não
tem escrita roda sem o dono aprovar, e aprovar sem ler o que o PR muda em `.github/` entrega root na VPS. Endurecer
depois: runner num usuário próprio com sudo só para o `deploy.sh`, ou deploy sem runner na VPS (SSH com comando forçado
a partir do runner do GitHub, ou a VPS buscando a release).

**Também:** secret scanning e push protection ligados (grátis em repositório público; vinham desligados). O token
padrão dos workflows já era só leitura.

**Evidência:** runs 40139099020 e 40141965238 recusados (2026-10-24); `gh repo edit --visibility public`, a política
lida de volta (`all_external_contributors`) e a tentativa 2 do run 40141965238 rodando sem bloqueio (2026-10-25); run
40198872362 verde nos seis jobs, com deploy, imagem pública e release v1.1.0.
