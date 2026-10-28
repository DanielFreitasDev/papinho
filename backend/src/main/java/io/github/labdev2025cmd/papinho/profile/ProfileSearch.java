package io.github.labdev2025cmd.papinho.profile;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Busca por nome de exibição no PostgreSQL. Nome e busca passam pelo {@code papinho_unaccent} (minúsculas, sem
 * acentos) e são quebrados em palavras em todo espaço ou pontuação (emoji e símbolos contam como pontuação); cada
 * palavra da busca precisa ser o começo de alguma palavra do nome. As palavras não têm {@code %}, {@code _} nem
 * {@code \}, e o {@code starts_with} compara texto puro: nada da entrada vira curinga, nem as formas largas
 * ({@code ％}) que o unaccent transforma nesses caracteres.
 */
@Component
class ProfileSearch {

    private static final String BY_NAME = """
            with query as (
                select array_remove(regexp_split_to_array(papinho_unaccent(:query), '[[:space:][:punct:]]+'), '')
                       as tokens)
            select p.user_id, starts_with(array_to_string(n.words, ' '), array_to_string(q.tokens, ' ')) as prefix
              from profiles p
             cross join query q
             cross join lateral (
                   select array_remove(regexp_split_to_array(papinho_unaccent(p.display_name),
                                                             '[[:space:][:punct:]]+'), '') as words) n
             where cardinality(q.tokens) > 0
               and not exists (select 1 from unnest(q.tokens) as t(token)
                                where not exists (select 1 from unnest(n.words) as w(word)
                                                   where starts_with(w.word, t.token)))
             order by prefix desc, array_to_string(n.words, ' '), p.user_id
             limit :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    ProfileSearch(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Quem tem o nome começando pela busca inteira vem antes; depois, em ordem alfabética sem acentos. */
    List<ProfileService.NameMatch> byName(String query, int limit) {
        return jdbc.query(BY_NAME, new MapSqlParameterSource("query", query).addValue("limit", limit),
                (row, index) -> new ProfileService.NameMatch(row.getObject("user_id", UUID.class),
                        row.getBoolean("prefix")));
    }
}
