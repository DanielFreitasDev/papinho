package io.github.labdev2025cmd.papinho.contact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Normalização da busca de pessoas, antes de contar os caracteres e consultar o banco. */
class ContactServiceTest {

    @Test
    @DisplayName("tira as pontas e o @ inicial e junta os espaços, inclusive os invisíveis e os de controle")
    void shouldNormalizeQuery() {
        assertThat(ContactService.normalizeQuery("  dan   fre ")).isEqualTo("dan fre");
        assertThat(ContactService.normalizeQuery("@dan")).isEqualTo("dan");
        assertThat(ContactService.normalizeQuery(" @ dan")).isEqualTo("dan");
        assertThat(ContactService.normalizeQuery("@@dan")).as("só um @ sai").isEqualTo("@dan");
        assertThat(ContactService.normalizeQuery("ana  paula\t\nsilva")).isEqualTo("ana paula silva");
        assertThat(ContactService.normalizeQuery("a\u0000b")).as("o NUL que o PostgreSQL recusa").isEqualTo("a b");
        assertThat(ContactService.normalizeQuery("João")).as("NFC, como os nomes guardados").isEqualTo("João");
        assertThat(ContactService.normalizeQuery("dan@")).isEqualTo("dan@");
        assertThat(ContactService.normalizeQuery("   ")).isEmpty();
    }
}
