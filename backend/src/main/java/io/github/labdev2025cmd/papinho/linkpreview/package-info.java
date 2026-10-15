/**
 * Prévia de links gerada pelo servidor (MD-04, MD-05): Open Graph e oEmbed (YouTube sem cookies, Vimeo e
 * Spotify) buscados com proteção contra SSRF — só http(s) nas portas padrão, endereços privados, de loopback e
 * link-local recusados depois de resolver o DNS (a conexão usa o endereço já checado), poucos
 * redirecionamentos, tamanho e tempo limitados. As imagens passam pelo servidor, sem metadados.
 */
@ApplicationModule(displayName = "Prévia de links")
@NullMarked
package io.github.labdev2025cmd.papinho.linkpreview;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
