package io.github.labdev2025cmd.papinho.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;
import io.github.labdev2025cmd.papinho.admin.RegistrationMode;
import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import io.github.labdev2025cmd.papinho.shared.security.PapinhoUser;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Informações públicas da instância para a tela de entrada: modo de cadastro e se ainda não há nenhum usuário
 * (então o primeiro cadastro vira admin, ID-08). Também verifica disponibilidade de {@code @usuario}.
 */
@RestController
@RequestMapping("/api/v1")
class InstanceController {

    private final InstanceSettingsService settings;
    private final IdentityService identity;
    private final EmailRecoveryService emailRecovery;
    private final RateLimiter rateLimiter;

    InstanceController(InstanceSettingsService settings, IdentityService identity,
            EmailRecoveryService emailRecovery, RateLimiter rateLimiter) {
        this.settings = settings;
        this.identity = identity;
        this.emailRecovery = emailRecovery;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/instance")
    InstanceResponse instance() {
        var current = settings.current();
        return new InstanceResponse("Papinho", current.registrationMode(), identity.isSetupRequired(),
                current.messageEditWindow().toMinutes(), current.messageDeleteWindow().toMinutes(),
                emailRecovery.enabled());
    }

    @GetMapping("/usernames/availability")
    AvailabilityResponse availability(@RequestParam String username, HttpServletRequest request) {
        rateLimiter.consume(RateLimiter.Policy.USERNAME_CHECK_BY_IP, request.getRemoteAddr());
        return new AvailabilityResponse(identity.isUsernameAvailable(username,
                CurrentUser.get().map(PapinhoUser::id).orElse(null)));
    }

    /**
     * @param setupRequired nenhuma conta existe: o próximo cadastro cria o admin sem convite
     * @param messageEditWindowMinutes janela para editar mensagens (MS-05), para o menu esconder o que expirou
     * @param messageDeleteWindowMinutes janela para apagar para todos (MS-06)
     * @param emailRecovery a instância envia links de recuperação por e-mail (ID-05)
     */
    record InstanceResponse(String name, RegistrationMode registrationMode, boolean setupRequired,
            long messageEditWindowMinutes, long messageDeleteWindowMinutes, boolean emailRecovery) {
    }

    record AvailabilityResponse(boolean available) {
    }
}
