package io.github.labdev2025cmd.papinho.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import io.github.labdev2025cmd.papinho.messaging.NotificationRecipients;
import io.github.labdev2025cmd.papinho.messaging.NotificationRecipients.Recipient;
import io.github.labdev2025cmd.papinho.notification.NotificationPreferences.Preferences;
import io.github.labdev2025cmd.papinho.privacy.PrivacyService;
import tools.jackson.databind.json.JsonMapper;

/** A regra de quem é avisado (NT-03): geral, individuais, grupos, menções e som. */
class NotificationDecisionTest {

    private static final Preferences ALL = new Preferences(true, true, true, true, true);

    private final NotificationDispatcher dispatcher = new NotificationDispatcher(
            mock(NotificationRecipients.class), mock(NotificationPreferences.class), mock(PrivacyService.class),
            mock(PushSubscriptions.class), mock(PushGateway.class), mock(PushProperties.class),
            mock(SimpMessagingTemplate.class), new StaticMessageSource(), JsonMapper.builder().build(),
            mock(PushSessions.class));

    @Test
    @DisplayName("com as notificações desligadas, nada avisa")
    void shouldRespectGlobalSwitch() {
        Preferences off = new Preferences(false, true, true, true, true);
        assertThat(dispatcher.decide(direct(), off, false)).isNull();
        assertThat(dispatcher.decide(group(true), off, false)).isNull();
    }

    @Test
    @DisplayName("grupos desligados: só a menção avisa, e só se as menções estiverem ligadas")
    void shouldRespectGroupsAndMentions() {
        assertThat(dispatcher.decide(group(false), ALL, false)).isNotNull();
        Preferences noGroups = new Preferences(true, true, false, true, true);
        assertThat(dispatcher.decide(group(false), noGroups, false)).as("mensagem de grupo").isNull();
        assertThat(dispatcher.decide(group(true), noGroups, false)).as("menção").isNotNull();
        Preferences nothing = new Preferences(true, true, false, false, true);
        assertThat(dispatcher.decide(group(true), nothing, false)).as("menção com menções desligadas").isNull();
        assertThat(dispatcher.decide(direct(), noGroups, false)).as("individual continua").isNotNull();
    }

    @Test
    @DisplayName("som desligado: o aviso sai silencioso")
    void shouldRespectSound() {
        assertThat(dispatcher.decide(direct(), ALL, false).silent()).isFalse();
        Preferences quiet = new Preferences(true, true, true, true, false);
        assertThat(dispatcher.decide(direct(), quiet, false).silent()).isTrue();
        assertThat(dispatcher.decide(direct(), quiet, true).silent()).as("também com o conteúdo oculto").isTrue();
    }

    private static Recipient direct() {
        return new Recipient(UUID.randomUUID(), UUID.randomUUID(), false, null, "Ana", "oi", false, false, false,
                false, false);
    }

    private static Recipient group(boolean mentioned) {
        return new Recipient(UUID.randomUUID(), UUID.randomUUID(), true, "Turma", "Ana", "oi", mentioned, false,
                false, false, false);
    }
}
