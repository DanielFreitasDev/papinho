---
name: Redirecionamento em builtin especial encerra o sh
description: Em sh não interativo, `{ : < /dev/tty; }` sem terminal encerra o script inteiro, mesmo dentro de if
type: project
category: bug
date: 2026-10-15
---
POSIX manda o shell não interativo sair quando um redirecionamento falha num builtin especial (`:`,
`exec`, `.`...). O `install-ca.sh` testava o terminal com `{ : < /dev/tty; }` e, rodando via `curl | sh`
sem terminal (contêiner, CI), saía em silêncio logo depois de conferir a impressão digital.

**Problema/Contexto:** nenhuma mensagem de erro; `set -e` e o `if` não protegem.
**Por que importa:** o script parecia funcionar em terminais e falhava em automação.
**Como aplicar:** testar redirecionamentos num subshell: `if (exec < /dev/tty) 2>/dev/null; then`.
**Evidência:** commit `fix(pki): corrige install-ca.sh sem terminal...`; `scripts/verify-install-ca.sh`
passou em Debian, Fedora e Arch depois da correção.
