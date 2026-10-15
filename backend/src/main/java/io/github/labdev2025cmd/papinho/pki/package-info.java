/**
 * PKI própria para HTTPS na rede local (ADR 0007, PK-01 a PK-06): subcomando {@code pki}, porta HTTP restrita
 * a {@code /setup/**} e página de instalação da CA. Desligada por padrão; em produção o TLS vem do proxy.
 */
@ApplicationModule(displayName = "PKI da LAN")
@NullMarked
package io.github.labdev2025cmd.papinho.pki;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
