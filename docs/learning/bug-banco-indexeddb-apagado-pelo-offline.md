---
name: Banco IndexedDB "papinho-*" é apagado ao abrir o app
description: o isolamento por conta do offline apaga todo banco com prefixo papinho- que não seja o da conta atual; dado do aparelho usa outro nome (papinho.device)
type: project
category: bug
date: 2026-10-27
---
Não crie banco IndexedDB com o prefixo `papinho-`: ao abrir o app, `lib/offline-db.ts` apaga todo banco
`papinho-*` que não seja o da conta atual (`papinho-<userId>`), para isolar as contas no mesmo navegador (NT-05).

**Problema/Contexto:** a imagem própria do papel de parede (UI-02, `lib/wallpaper-image.ts`) ficava num banco
`papinho-device`. Ela aparecia logo depois de escolher e também na primeira recarga (a leitura vinha antes da
limpeza), mas na segunda abertura o banco já não existia e o papel de parede voltava ao padrão.
**Por que importa:** o sintoma é intermitente (depende da ordem entre a leitura e a limpeza) e parece bug do
IndexedDB ou da imagem.
**Como aplicar:** dado do aparelho, que não é de uma conta, fica fora do prefixo (`papinho.device`, com ponto). Dado
de uma conta vai no banco dela (`offline-db.ts`) e some no logout. O `onLogout` de `lib/logout-cleanup.ts` apaga
o que precisar sumir na saída (a imagem do papel de parede também).
**Evidência:** script de sonda listando `indexedDB.databases()` a cada navegação: `papinho-device` existia depois de
escolher a imagem e sumia depois de abrir `/`; o `chat-look.spec.ts` reprovava em "Remover imagem" antes de
`e8d1601` e passou depois (desktop e celular, 2026-10-27).
