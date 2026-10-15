package io.github.labdev2025cmd.papinho.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Configurações da instância no painel do admin (AD-01, AD-02). */
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
class AdminSettingsController {

    private final InstanceSettingsService service;

    AdminSettingsController(InstanceSettingsService service) {
        this.service = service;
    }

    @GetMapping
    InstanceSettingsResponse get() {
        return InstanceSettingsResponse.from(service.current());
    }

    @PutMapping
    InstanceSettingsResponse update(@Valid @RequestBody UpdateInstanceSettingsRequest request) {
        return InstanceSettingsResponse.from(service.update(request));
    }

    /** Configurações expostas ao admin, em unidades legíveis. */
    record InstanceSettingsResponse(RegistrationMode registrationMode, long usernameChangeCooldownDays,
            long messageEditWindowMinutes, long messageDeleteWindowHours, long userQuotaMegabytes,
            boolean breachedPasswordCheck) {

        static InstanceSettingsResponse from(InstanceSettings settings) {
            return new InstanceSettingsResponse(settings.registrationMode(),
                    settings.usernameChangeCooldown().toDays(), settings.messageEditWindow().toMinutes(),
                    settings.messageDeleteWindow().toHours(), settings.userQuotaBytes() / (1024 * 1024),
                    settings.breachedPasswordCheck());
        }
    }
}
