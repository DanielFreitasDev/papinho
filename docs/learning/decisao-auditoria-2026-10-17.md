---
name: Decisões da correção da auditoria de 2026-10-17
description: convite do admin no modo fechado, token da foto, HEIC recusado, exportação sem trancadas, presença por mudança
type: project
category: decision
date: 2026-10-17
---
- **Modo fechado (A06):** vale o JavaDoc e a tela: o convite de quem é admin (ativo) agora reabre a porta; o de
  outra pessoa aparece inválido no status e é recusado no cadastro.
- **Visualização única (A15):** a foto sai numa resposta só (o token morre nela, troca atômica no banco); vídeo
  e áudio mantêm o token de 5 min por causa do `Range` (ADR 0012 atualizada).
- **Fotos que não dá para limpar (A20):** JPEG e PNG acima de 50 MB perdem os metadados em fluxo
  (`PhotoMetadata`, sem decodificar) e viram documento; HEIC/HEIF/AVIF e outras fotos grandes são recusadas
  com `photo-metadata` (415). Recusar é melhor que guardar a localização.
- **Exportação com conversa trancada (A25):** sem o token da requisição, a exportação leva só que a conversa
  existe (`trancada: true`); textos, rascunhos, agendadas, com quem é e os anexos ficam de fora. O link de
  download não manda cabeçalho; para levá-las, a pessoa destranca antes (ADR 0013 atualizada).
- **Presença (A02):** cada par recebe o evento só quando a visão filtrada dele muda; ao ficar online vai o último
  visto por último guardado, não nulo.
- **Cache de prévias (A13):** apagar para todos e o job das temporárias apagam as prévias cujas URLs aparecem no
  texto; outra mensagem com o mesmo link busca de novo.
- **Padrões da instância nos testes (A07):** o `DatabaseCleaner` restaura a linha que a migration gravou (lida
  uma vez por banco) em vez de reescrever os padrões à mão, que escondia um padrão inseguro.

**Evidência:** commits `294522b`, `af455c9`, `a6765f1`, `bb5e826`, `7af2faa`, `b3980b3`, `208f3ab` e os testes
citados neles.
