---
name: Paletas de cor e papéis de parede (UI-02)
description: paletas por fórmula OKLCH com contraste conferido, hover por color-mix, fundo da conversa e bolha recebida, desenhos do Lucide como máscara e imagem própria só no aparelho
type: project
category: decision
date: 2026-10-27
---
Como o app ganhou cor (pedido do dono em 2026-10-27) sem quebrar contraste nem a CSP:

- **Paletas por fórmula:** cada paleta (`data-palette` no `<html>`) declara só matiz, força e luminosidade da cor da
  marca no claro e no escuro (`--hue`, `--brand-c/cd`, `--brand-l/ld`); fundos, bordas, lateral e textos saem de
  fórmulas OKLCH comuns no `index.css`. Uma paleta nova são cinco números. "Grafite" é o preto e branco original e o
  padrão passou a ser o verde Papinho (a cor do ícone e do manifesto).
- **Contraste conferido antes:** um script calculou OKLCH → sRGB → luminância para todo par de texto e fundo (marca
  sobre fundo, lateral, cartão e muted; texto apagado sobre os fundos; texto sobre a bolha) e para o gamut sRGB: tudo
  ≥ 4,5:1. O que o script não pegou foi um estado: o hover `bg-primary/80` clareava o verde (3,82:1, axe). O hover
  do botão primário e do Badge passou a ser `color-mix` com 14% da cor do texto do tema, que escurece no claro e
  clareia no escuro.
- **Fundo da conversa:** token `--chat` (um tom da matiz) e a bolha recebida com a cor do cartão (variante
  `incoming` do Bubble), como no WhatsApp; senão a bolha `muted` sumia no fundo colorido. Avisos e separadores de dia
  viraram pílulas com fundo próprio, legíveis sobre qualquer papel de parede.
- **Desenhos (doodles):** o desenho do WhatsApp não pode ser copiado; o padrão é gerado por
  `frontend/scripts/make-doodles.mjs` com ícones do Lucide (ISC, aviso dentro do SVG; comentário XML não aceita
  `--`, o script troca por `-`) e usado como `mask-image`, pintado com uma cor do tema.
- **Imagem própria:** fica só no aparelho (IndexedDB `papinho.device`, ver o bug do prefixo), redesenhada num canvas
  (sem EXIF, até 2560 px) e mostrada por URL `blob:` numa propriedade do CSSOM (a CSP já aceita `blob:` em `img-src`
  e só barra estilo inline no HTML). Sai junto no logout.
- **Amostras:** rádios do Base UI com `data-swatch`; as cores vêm do CSS (a mesma regra da paleta ou do papel de
  parede), sem `style` inline.

**Evidência:** saída do script de contraste (pior par 4,80:1 no claro, todos dentro do sRGB depois do ajuste de
croma); axe do `a11y.spec.ts` (claro e escuro, 320 px) e do `organize.spec.ts` verdes depois de `efdd20c`;
`chat-look.spec.ts`; commits `63510dc` e `dffef65` (2026-10-27).
