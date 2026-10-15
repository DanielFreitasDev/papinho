package io.github.labdev2025cmd.papinho.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Alteração das configurações da instância pelo admin. */
public record UpdateInstanceSettingsRequest(
        @NotNull RegistrationMode registrationMode,
        @Min(0) @Max(365) int usernameChangeCooldownDays,
        @Min(0) @Max(10_080) int messageEditWindowMinutes,
        @Min(0) @Max(8_760) int messageDeleteWindowHours,
        @Min(1) @Max(10_485_760) long userQuotaMegabytes,
        boolean breachedPasswordCheck) {
}
