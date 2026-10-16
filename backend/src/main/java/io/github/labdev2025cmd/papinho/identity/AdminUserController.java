package io.github.labdev2025cmd.papinho.identity;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Usuários no painel do admin (AD-01). */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController {

    private final AdminUserService service;

    AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse list(@RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<AdminUserService.AdminUserView> result = service.list(CurrentUser.id(), query, page, size);
        return new PageResponse(result.getContent(), result.getNumber(), result.getTotalElements(),
                result.getTotalPages());
    }

    @PostMapping("/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disable(@PathVariable UUID id) {
        service.disable(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void enable(@PathVariable UUID id) {
        service.enable(CurrentUser.id(), id);
    }

    @PutMapping("/{id}/admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void promote(@PathVariable UUID id) {
        service.changeAdmin(CurrentUser.id(), id, true);
    }

    @DeleteMapping("/{id}/admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void demote(@PathVariable UUID id) {
        service.changeAdmin(CurrentUser.id(), id, false);
    }

    @PostMapping("/{id}/reset-password")
    TemporaryPasswordResponse resetPassword(@PathVariable UUID id) {
        return new TemporaryPasswordResponse(service.resetPassword(CurrentUser.id(), id));
    }

    record PageResponse(List<AdminUserService.AdminUserView> items, int page, long totalItems, int totalPages) {
    }

    record TemporaryPasswordResponse(String temporaryPassword) {
    }
}
