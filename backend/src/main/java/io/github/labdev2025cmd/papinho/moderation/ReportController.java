package io.github.labdev2025cmd.papinho.moderation;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Denunciar (CT-04) e a fila de denúncias do admin (AD-01). */
@RestController
class ReportController {

    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/api/v1/reports")
    @ResponseStatus(HttpStatus.CREATED)
    void report(@Valid @RequestBody ReportRequest body) {
        reports.report(CurrentUser.id(), body.conversationId(), body.reason(), body.block());
    }

    @GetMapping("/api/v1/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    ReportService.ReportPage list(@RequestParam(defaultValue = "true") boolean open,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.list(open, page, size);
    }

    @PostMapping("/api/v1/admin/reports/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resolve(@PathVariable UUID id) {
        reports.close(CurrentUser.id(), id, false);
    }

    @PostMapping("/api/v1/admin/reports/{id}/dismiss")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(@PathVariable UUID id) {
        reports.close(CurrentUser.id(), id, true);
    }

    record ReportRequest(@NotNull UUID conversationId, @Size(max = 500) @Nullable String reason, boolean block) {
    }
}
