---
name: Pegadinhas da correção dos achados Baixos (2026-10-17)
description: Enter no celular, safe area pelo CDP, Mockito com varargs, mutação equivalente, 416 do Spring, Prettier e shadcn add
type: project
category: gotcha
date: 2026-10-17
---
- **Composer no celular (A85):** no projeto `mobile-chromium` o Enter quebra a linha; o e2e precisa enviar pelo botão
  "Enviar". Dentro da conversa a lista lateral não aparece: espere o conteúdo na própria conversa.
- **Safe area (A89):** o Chromium 1194 aceita `Emulation.setSafeAreaInsetsOverride` pelo CDP
  (`{ insets: { left, right } }`); com isso o e2e prova o recuo do `env(safe-area-inset-*)` sem aparelho.
- **Mockito 5:** `verify(jdbc).update(anyString(), any(), any())` não casa com `update(String, Object...)`; use
  `any(Object[].class)`. `List.<Object>of(new String[] {...})` espalha o array como varargs: faça o cast `(Object)`.
- **Mutação equivalente:** a mutação da auditoria pode ficar sem efeito depois de outra mudança (A73: o global de
  leitura só é lido na conversa individual; A58: o `isPublicIpv4` também recusa 127/8). Antes de concluir que o
  teste falta, confira se a mutação ainda muda o comportamento e escreva a que muda.
- **Range inválido (A82):** com `ResponseEntity<Resource>`, o Spring responde 416 com `Content-Range` mas manda o
  arquivo inteiro no corpo. Confira a faixa com `HttpRange.toResourceRegions` antes e responda o Problem Details.
- **Prettier:** o `format:check` só cobre `.ts`/`.tsx`. `prettier --write` num `.css` reformata linhas que não são da
  mudança; edite o CSS no estilo do arquivo.
- **`shadcn add`:** o CLI pergunta se sobrescreve o `button.tsx` (que tem ajustes do projeto, como os 44 px); rode
  com `yes n |`. O import `cn` do arquivo gerado é um alias do projeto e funciona.
- **Playwright Firefox/WebKit no sandbox:** `PLAYWRIGHT_BROWSERS_PATH=<pasta> pnpm exec playwright install firefox webkit`
  baixa, mas o WebKit só abre depois de `pnpm exec playwright install-deps firefox webkit` (apt, como root).
- **Duas instâncias, um banco:** o JAR padrão e o do perfil lan precisam do mesmo `PAPINHO_DATA_DIR` (a mesma
  `secret.key`); com diretórios diferentes, a chave VAPID cifrada por um não abre no outro e o WebKit (que usa a lan)
  recebe uma rajada de 500 ("Unable to decrypt secret"). O CI já usa o mesmo diretório.
- **JAR e e2e:** cada mutação de frontend exige gerar o JAR de novo, e o JAR mutado fica em `backend/target`; gere o
  certo antes do próximo e2e.

**Evidência:** commits dos achados A58, A65, A73, A82, A85, A89 e A95 desta passada; rodadas registradas no STATUS.

**Aplicação:** testes de backend com Mockito, e2e no celular e auditorias por mutação.
