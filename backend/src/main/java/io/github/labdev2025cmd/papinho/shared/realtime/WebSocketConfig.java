package io.github.labdev2025cmd.papinho.shared.realtime;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * STOMP sobre WebSocket em {@code /ws}, na mesma origem do SPA (ADR 0004). Broker simples em memória com
 * destinos por usuário ({@code /user/queue/...}); o cliente manda comandos para {@code /app/...}. Sem SockJS:
 * todos os navegadores suportados falam WebSocket.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer, DisposableBean {

    /** Tamanho máximo de um frame recebido: comandos do cliente são pequenos (digitando, presença). */
    private static final int MAX_INBOUND_BYTES = 16 * 1024;

    /** Agendador só dos heartbeats do broker; fora do contexto para não competir com o dos @Scheduled. */
    private final ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();
    private final RealtimeSessions sessions;

    WebSocketConfig(RealtimeSessions sessions) {
        this.sessions = sessions;
        heartbeats.setPoolSize(1);
        heartbeats.setThreadNamePrefix("stomp-heartbeat-");
        heartbeats.initialize();
    }

    @Override
    public void destroy() {
        heartbeats.shutdown();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Sem setAllowedOrigins: só a mesma origem pode abrir o WebSocket (proteção contra CSWSH).
        // O id da sessão HTTP vai para os atributos para derrubar o WebSocket quando ela acabar.
        registry.addEndpoint("/ws").addInterceptors(new HttpSessionHandshakeInterceptor());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue").setHeartbeatValue(new long[] {20_000, 20_000})
                .setTaskScheduler(heartbeats);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_INBOUND_BYTES).addDecoratorFactory(sessions::decorate);
    }
}
