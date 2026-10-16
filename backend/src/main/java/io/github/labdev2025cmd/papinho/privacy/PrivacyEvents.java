package io.github.labdev2025cmd.papinho.privacy;

import java.util.UUID;

/** Eventos do módulo de privacidade. */
public final class PrivacyEvents {

    private PrivacyEvents() {
    }

    /** Mudou quem vê a presença de {@code userId} (ou o que ele vê dos outros, pela reciprocidade). */
    public record PresenceVisibilityChanged(UUID userId) {
    }

    /** Mudou a confirmação de leitura global de {@code userId} (PV-06). */
    public record ReadReceiptsChanged(UUID userId) {
    }
}
