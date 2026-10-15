/**
 * Contatos e bloqueios (CT-01, CT-03): adicionar por {@code @usuario}, link ou QR Code, apelido local,
 * bloquear e desbloquear. Expõe {@link io.github.labdev2025cmd.papinho.contact.ContactQueries} para os outros
 * módulos e aplica o bloqueio à foto e ao recado pelo perfil.
 */
@ApplicationModule(displayName = "Contatos")
@NullMarked
package io.github.labdev2025cmd.papinho.contact;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
