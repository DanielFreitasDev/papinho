# LEARNING.md — base de conhecimento do projeto

Leia as entradas relacionadas à tarefa. Os detalhes ficam em `docs/learning/`.

## Bugs e soluções
- [Handler /assets/** precisa apontar para a subpasta](docs/learning/bug-handler-de-assets-sem-subpasta.md) — o exemplo do guia dava 404 em todo asset com hash.
- [Redirecionamento em builtin especial encerra o sh](docs/learning/bug-sh-redirecionamento-em-builtin-especial.md) — teste de /dev/tty sempre num subshell.

## Pegadinhas de ambiente, dependências e bibliotecas
- [PKIX do JDK/BC ignora Name Constraints da âncora](docs/learning/gotcha-pkix-ignora-name-constraints-da-ancora.md) — prove a CA da LAN com openssl verify ou certutil.
- [Ambiente de nuvem: Docker, imagens e processos](docs/learning/gotcha-ambiente-nuvem-docker-e-processos.md) — iniciar dockerd, espelhos contra 429, JDK 25, cuidado com pkill -f.
- [MockMvc não serializa SameSite](docs/learning/gotcha-mockmvc-nao-serializa-samesite.md) — atributos de cookie só se provam no servidor real.

## Decisões de arquitetura
- As decisões vivem em `docs/adr/` (0001–0010).

## Abordagens que NÃO funcionaram

## Notas de performance

## Referências externas
