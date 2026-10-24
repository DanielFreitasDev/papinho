---
name: Duração de áudio — WebM do MediaRecorder, pre-skip e tipos do Tika
description: o WebM gravado não diz a duração (Infinity; o Firefox grava 0,0); a conta que bate com o navegador e os nomes de áudio que o Tika devolve
type: project
category: gotcha
date: 2026-10-24
---
A duração que o navegador toca é **fim do último bloco − início do primeiro − CodecDelay** no WebM e
**(grânulo final − pre-skip) / 48 000** no Ogg Opus; o ffprobe inclui o pre-skip (6,5 ms a mais) e não serve de gabarito.

**Problema/Contexto:** o MediaRecorder do Chromium e do Firefox grava WebM sem `Duration` (o elemento `<audio>` diz
`Infinity`; o Firefox escreve `Duration = 0,0` como marcador) e com segmento e clusters de tamanho desconhecido. O
Chromium põe 3 quadros Opus de 20 ms por bloco (TOC `0xfb`, código 3) e não declara `CodecDelay`; o Firefox declara
`CodecDelay` de 6,5 ms e começa os clusters em 6 ms. O WebM do FFmpeg termina com `DiscardPadding` no último bloco; o
MP4 fragmentado do FFmpeg não tem lista de edição (conta as 1.024 amostras de preparo do AAC). O WebKit do Playwright
no Linux não tem `MediaRecorder`: o MP4 do Safari não dá para gravar aqui (o fragmentado foi coberto pelo FFmpeg).
**Por que importa:** com a conta errada, a duração mostrada antes do play difere da do tocador em 1 s perto das
viradas de segundo; um `Duration` 0,0 aceito como valor vira "0:00". O Tika chama `.aac` de `audio/x-aac` e `.flac` de
`audio/x-flac` (iam como documento) e um Ogg Opus chamado `.ogg` de `audio/vorbis`.
**Como aplicar:** a duração dos áudios vem do servidor (`media/internal/audio/AudioDuration`, lida no upload e por job
para os antigos) e o cliente mede no aparelho só para a bolha enquanto sobe (`measureAudioDuration`: metadados do
elemento; para o WebM, `decodeAudioData` num `OfflineAudioContext`, que não pede gesto do usuário). Fixture nova de
gravação: grave com o microfone falso do Playwright e use como gabarito o `decodeAudioData` do próprio navegador.
Tipos de áudio novos entram no `ContentTypes.ALIASES` com o nome que todo navegador toca.
**Evidência:** `AudioDurationTest` (34) com `chromium.webm` 2280 ms, `firefox.webm` 2274 ms e `firefox.ogg` 2251 ms,
iguais ao `decodeAudioData` medido nos navegadores (2,28 s, 2,2735 s e 2,2507 s; o ffprobe deu N/A, N/A e 2,257 s);
`opus.webm` do FFmpeg dava 2515 ms sem o `DiscardPadding` e 2501 ms com ele; `MediaIT.shouldReadAudioDuration` com AAC,
FLAC, WAV, MP3, M4A e Ogg; 2026-10-24.
