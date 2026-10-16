---
name: Cadastros em fila pelo advisory lock do primeiro usuário
description: a trava que decide o primeiro admin ficava presa durante os 11 hashes Argon2 de todo cadastro
type: project
category: bug
date: 2026-10-16
---
Um advisory lock de transação (`pg_advisory_xact_lock`) só para decidir uma corrida rara não pode envolver
trabalho caro: ele fica preso até o commit e enfileira todo mundo.

**Problema/Contexto:** `IdentityService.register` pegava a trava global antes de tudo para decidir, sem corrida,
quem é o primeiro usuário (ID-08). A transação ainda faz 11 hashes Argon2 (senha + 10 códigos de recuperação,
~260 ms nesta máquina, bem mais num servidor caseiro modesto), então cadastros simultâneos saíam um por vez: nos
logs, "Account registered" a cada ~0,8 s, e o último de N amigos entrando pelo mesmo convite esperava N × 0,8 s.
No e2e local (12 workers), o diálogo dos códigos não aparecia em 5 s.
**Por que importa:** o momento típico do produto é o anfitrião mandar o convite num grupo e todos se cadastrarem
juntos; num Raspberry Pi o último esperaria dezenas de segundos.
**Como aplicar:** a trava só vale enquanto `users.count() == 0` (a contagem nunca volta a zero: o último admin não
sai). Com alguém cadastrado, ninguém mais vira o primeiro e não há trava. Vale para qualquer trava global: pegue-a só
no trecho que decide a corrida, depois do trabalho caro ou fora dele.
**Evidência:** `IdentityIT.shouldNotSerializeLaterRegistrations` (com a trava presa por outra conexão, o cadastro
termina; sem a correção, estoura 10 s) e `shouldSerializeFirstRegistration`; commit `4bb71eb`; e2e local sem as
falhas de cadastro depois da correção.
