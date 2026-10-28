---
name: Prévia de PDF num processo Java à parte
description: o PDFBox 3.0.8 descomprime cada fluxo inteiro na memória, e o MemoryUsageSetting não vale para isso; a prévia roda num processo filho com 256 MB e 20 s
type: project
category: decision
date: 2026-10-28
---
A prévia dos PDFs (número de páginas e primeira página, MD-01) roda num processo Java filho (`PdfPreviewWorker`), um
por vez, com `-Xmx256m`, `-XX:+ExitOnOutOfMemoryError` e prazo de 20 s; o servidor nunca carrega o PDFBox.

**Problema/Contexto:** no PDFBox 3.0.8, `Filter.decode` descomprime cada fluxo para um `RandomAccessReadWriteBuffer`
em memória, sem teto. O `MemoryUsageSetting` (stream cache misto ou só em arquivo) vale só para fluxos novos ou
alterados, como os decifrados. Um PDF de poucas centenas de KB vira gigabytes ao abrir, e a imagem Docker roda com
`-XX:+ExitOnOutOfMemoryError`: o servidor sairia. Pegadinha do teste: o conteúdo da página com `/Filter /FlateDecode`
solto é lido aos poucos (`PDPage.getContentsForStreamParsing`); com `[/FlateDecode]` (lista), como fontes, imagens e
formulários, vai inteiro para a memória.
**Decisão:** `PdfPreview` sobe o filho, espera até 20 s e lê o que ele publicou (`result.txt` gravado por renomeação,
com a contagem antes do desenho, para ela sobreviver a um estouro). Sem vez em 10 s ou sem como subir o processo, o
PDF fica com `pages_pending` para o `PdfPagesBackfillJob`. A prévia tem 480 px de largura (a das miniaturas) e altura
até 960 px (página mais comprida que 1:2 mostra só o topo); acima de 100 MB o PDF nem é aberto.
**Alternativas descartadas:** analisar no servidor com prazo checado a cada operador (não segura a memória de um
fluxo só); pré-varrer o arquivo atrás de fluxos Flate (filtros em cadeia, fluxos cifrados e imagens dentro do
conteúdo escapam).
**Como aplicar:** no JAR do Spring Boot (classe carregada pelo `LaunchedClassLoader`) o filho sobe por
`-cp papinho.jar -Dloader.main=<classe> org.springframework.boot.loader.launch.PropertiesLauncher`; fora dele, com o
`java.class.path`. O Logback sem configuração formataria tudo em DEBUG no filho:
`-Dslf4j.provider=org.slf4j.helpers.NOP_FallbackServiceProvider`. O PDFBox grava a lista das fontes do sistema em
`~/.pdfbox.cache`; `-Dpdfbox.fontcache` a manda para a pasta dos uploads. Cada processo pode ocupar ~300 MB além do
servidor, dentro do mesmo limite (o cgroup do contêiner ou o `MemoryMax=1536M` da unit). Por isso o filho sobe o
próprio `oom_score_adj` para 1000 (subir não pede privilégio), e o kernel o escolhe primeiro quando a memória acaba; e
a unit usa `OOMPolicy=continue`, porque o padrão `stop` do systemd encerra o serviço inteiro quando o kernel mata
qualquer processo dele. O `deploy.sh` só troca o JAR: a unit da VPS se atualiza à mão (`deploy/PRODUCAO.md`).
**Evidência:** `PdfPreviewTest` (6): bomba de 512 MB num PDF de ~500 KB, o filho sai com código 3 e o teste segue,
com a página contada; com `/FlateDecode` solto a mesma bomba desenhava em 9,9 s sem estourar. `MediaIT`
(`shouldPreviewPdfFirstPage`, `shouldBackfillPagesOfOldPdfs`). Medido em 2026-10-28: ~0,3 s por PDF no classpath dos
testes; pelo `papinho.jar` empacotado, 0,7 s (1,0 s na primeira vez, varrendo as fontes). Lido no `/proc` dos 6
filhos do `PdfPreviewTest`: `oom_score_adj` 0 na partida da JVM e 1000 assim que o `main` roda.
