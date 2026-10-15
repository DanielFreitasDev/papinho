package io.github.labdev2025cmd.papinho.shared.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.web.csrf.CsrfChannelInterceptor;

/**
 * Autorização das mensagens STOMP: só usuário autenticado (a sessão vem do handshake HTTP), só assina a própria
 * fila ({@code /user/queue/**}) e só envia para {@code /app/**}. O resto é negado.
 */
@Configuration
@EnableWebSocketSecurity
class WebSocketSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages
                .simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.HEARTBEAT, SimpMessageType.UNSUBSCRIBE,
                        SimpMessageType.DISCONNECT).authenticated()
                .simpSubscribeDestMatchers("/user/queue/**").authenticated()
                .simpDestMatchers("/app/**").authenticated()
                .anyMessage().denyAll()
                .build();
    }

    /**
     * CSRF no CONNECT comparando o valor cru do cookie {@code XSRF-TOKEN} com o cabeçalho {@code X-XSRF-TOKEN},
     * como o SPA já faz nas requisições HTTP ({@code csrf.spa()}). O padrão do Spring espera o token mascarado.
     */
    @Bean
    ChannelInterceptor csrfChannelInterceptor() {
        return new CsrfChannelInterceptor();
    }
}
