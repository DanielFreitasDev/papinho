/**
 * Contatos e bloqueios (CT-01, CT-03): adicionar por {@code @usuario}, link ou QR Code, apelido local,
 * bloquear e desbloquear, e a busca de pessoas da instância pelo nome ou pelo começo do {@code @usuario} (sugestões
 * ao adicionar contato). Expõe {@link io.github.labdev2025cmd.papinho.contact.ContactQueries} para os outros
 * módulos e aplica o bloqueio à foto e ao recado pelo perfil.
 *
 * <p>Quem não aparece na busca por nome é decidido pela porta
 * {@link io.github.labdev2025cmd.papinho.contact.NameSearchPolicy}, implementada pela privacidade, que depende deste
 * módulo (o contrário seria um ciclo).</p>
 */
@ApplicationModule(displayName = "Contatos")
@NullMarked
package io.github.labdev2025cmd.papinho.contact;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
