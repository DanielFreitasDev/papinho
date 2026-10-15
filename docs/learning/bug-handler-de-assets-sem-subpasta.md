---
name: Handler /assets/** precisa apontar para a subpasta assets/
description: Com addResourceHandler("/assets/**"), o caminho chega sem o prefixo; localização em META-INF/resources/ dá 404
type: project
category: bug
date: 2026-10-15
---
O exemplo de `SpaConfig` do `STRUCTURE.md` registra `/assets/**` com as localizações
`classpath:/META-INF/resources/`. O Spring entrega ao resolver só o trecho depois do padrão
(`app-abc123.js`), então todo asset com hash dava 404 (e o fallback não serve, porque tem ponto).

**Como aplicar:** localizações do handler de assets terminam em `.../assets/`.
**Evidência:** `SecurityIT.shouldCacheHashedAssets` falhava com 404 e passou após a correção
(`backend/src/main/java/.../shared/web/SpaConfig.java`).
