/**
 * Transcrição de áudios (AU-03, AU-05): sob demanda por quem recebe ou automática, com o resultado em cache no
 * servidor para os demais. Provedores no servidor seguem a API da OpenAI ({@code POST {base}/v1/audio/transcriptions})
 * — Groq, Whisper próprio, OpenAI ou URL personalizada — com o backend como proxy, chaves cifradas em repouso e
 * User-Agent {@code Papinho/<versão>}. O padrão (inclusive "no navegador", AU-04) é do admin; cada usuário escolhe.
 */
@ApplicationModule(displayName = "Transcrição")
@NullMarked
package io.github.labdev2025cmd.papinho.transcription;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
