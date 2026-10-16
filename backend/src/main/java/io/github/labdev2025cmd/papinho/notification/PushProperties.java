package io.github.labdev2025cmd.papinho.notification;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Web Push (prefixo {@code app.push}, NT-01).
 *
 * @param enabled envia push para os navegadores assinados (as notificações com o app aberto não dependem disso)
 * @param subject contato da instância no VAPID ({@code mailto:} ou {@code https:}), exigido pela Apple
 * @param allowedHosts sufixos de host aceitos como serviço de push: o servidor faz POST para a URL que o navegador
 *     informa, então só os serviços conhecidos passam (contra SSRF)
 * @param ttl por quanto tempo o serviço de push guarda a mensagem se o aparelho estiver desligado
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(@DefaultValue("true") boolean enabled,
        @DefaultValue("https://github.com/labdev2025-cmd/papinho") String subject,
        @DefaultValue({"fcm.googleapis.com", "push.services.mozilla.com", "notify.windows.com", "push.apple.com"})
        List<String> allowedHosts,
        @DefaultValue("12h") Duration ttl) {
}
