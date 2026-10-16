package io.github.labdev2025cmd.papinho.transcription;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Escolha de provedor de transcrição (BK-02). */
@Component
class TranscriptionPersonalData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    TranscriptionPersonalData(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String name() {
        return "transcricao";
    }

    @Override
    public JsonNode collect(UUID userId) {
        return PersonalDataSection.json(jdbc, json, """
                select to_jsonb(t) - 'user_id' from transcription_preferences t where t.user_id = :userId
                """, userId);
    }
}
