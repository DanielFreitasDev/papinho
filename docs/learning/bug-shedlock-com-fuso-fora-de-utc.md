---
name: Travas do ShedLock deslocadas num servidor fora de UTC
description: com usingDbTime, o ShedLock grava timezone('utc', CURRENT_TIMESTAMP) sem fuso; a coluna timestamptz o convertia pelo fuso da sessão, que o driver copia da JVM
type: project
category: bug
date: 2026-10-24
---
A sessão JDBC precisa estar sempre em UTC (`spring.datasource.hikari.connection-init-sql: SET TIME ZONE 'UTC'`): o
ShedLock com a hora do banco grava um horário sem fuso, e a coluna com fuso o convertia pelo fuso da máquina.

**Problema/Contexto:** o `JdbcTemplateLockProvider` com `usingDbTime()` (ShedLock 7.10.1) grava e compara
`timezone('utc', CURRENT_TIMESTAMP)`, um timestamp sem fuso com a hora de UTC. As colunas da `shedlock` têm fuso (a
regra do projeto é `timestamptz` em tudo, provada pelo `IdentityIT.shouldStoreEveryTimestampWithTimeZone`), então o
PostgreSQL convertia o valor pelo fuso da sessão, e o pgjdbc abre a sessão com o fuso da JVM. Numa máquina em
`America/Fortaleza`, toda trava ficava 3 h no futuro. O ShedLock continuava coerente consigo mesmo, mas qualquer SQL
com `now()`, e instâncias em fusos diferentes, não. O `DisappearingIT` da trava entrou na passada 11 (sandbox em UTC)
e falhava sempre na máquina local. `options=-c TimeZone=UTC` na URL não resolve: o pgjdbc manda também o parâmetro
`TimeZone` da JVM, e ele vence.
**Por que importa:** o Papinho roda em casa, com o fuso do sistema; os jobs com trava (temporárias, agendadas,
visualização única, limpezas) dependem dela. Tirar o fuso das colunas (como na documentação do ShedLock) contraria a
regra do `timestamptz` e foi descartado.
**Como aplicar:** não tire o `connection-init-sql`; SQL de teste usa `now()` normalmente. Para provar algo que depende
do fuso, crie um pool novo com `copyStateTo` da configuração da aplicação depois de `TimeZone.setDefault(...)` (o
driver lê o fuso da JVM a cada conexão), como no `ShedLockIT`. Ao provar uma correção tirando um arquivo de
`src/main/resources`, apague também a cópia em `target/classes`: o Flyway e o Spring a leem de lá.
**Evidência:** `shedlock` da instância local com `lock_until - now()` de 02:59:23 logo depois do job; sonda com o
pgjdbc 42.7.13 numa JVM em Fortaleza: sessão em `America/Fortaleza` com e sem `options=-c TimeZone=UTC`; `ShedLockIT`
falha com 11.100 s sem o `connection-init-sql` e passa com ele, e o `DisappearingIT` idem na máquina local;
2026-10-24.
