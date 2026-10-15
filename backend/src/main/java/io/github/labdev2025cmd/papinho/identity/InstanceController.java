package io.github.labdev2025cmd.papinho.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;
import io.github.labdev2025cmd.papinho.admin.RegistrationMode;
import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import io.github.labdev2025cmd.papinho.shared.security.PapinhoUser;

/**
 * Informações públicas da instância para a tela de entrada: modo de cadastro e se ainda não há nenhum usuário
 * (então o primeiro cadastro vira admin, ID-08). Também verifica disponibilidade de {@code @usuario}.
 */
@RestController
@RequestMapping("/api/v1")
class InstanceController {

    private final InstanceSettingsService settings;
    private final IdentityService identity;

    InstanceController(InstanceSettingsService settings, IdentityService identity) {
        this.settings = settings;
        this.identity = identity;
    }

    @GetMapping("/instance")
    InstanceResponse instance() {
        return new InstanceResponse("Papinho", settings.current().registrationMode(), identity.isSetupRequired());
    }

    @GetMapping("/usernames/availability")
    AvailabilityResponse availability(@RequestParam String username) {
        return new AvailabilityResponse(identity.isUsernameAvailable(username,
                CurrentUser.get().map(PapinhoUser::id).orElse(null)));
    }

    /**
     * @param setupRequired nenhuma conta existe: o próximo cadastro cria o admin sem convite
     */
    record InstanceResponse(String name, RegistrationMode registrationMode, boolean setupRequired) {
    }

    record AvailabilityResponse(boolean available) {
    }
}
