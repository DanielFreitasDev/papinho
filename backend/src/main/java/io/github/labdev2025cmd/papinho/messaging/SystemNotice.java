package io.github.labdev2025cmd.papinho.messaging;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Aviso do sistema numa conversa (mensagem {@link MessageKind#SYSTEM}), gravado como JSON no corpo. Os nomes
 * ficam como eram no momento do aviso; o cliente monta a frase traduzida a partir de {@code type}.
 *
 * @param type o que aconteceu: group.created, group.added, group.removed, group.left, group.joined,
 *     group.renamed, group.description, group.photo, group.settings, group.admin, group.notAdmin
 * @param actor quem fez
 * @param targets em quem (adicionados, removidos, promovidos)
 * @param value valor novo, quando há (nome do grupo)
 */
record SystemNotice(String type, Person actor, List<Person> targets, @Nullable String value) {

    record Person(UUID id, String name) {
    }
}
