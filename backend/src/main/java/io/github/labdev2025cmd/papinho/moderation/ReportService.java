package io.github.labdev2025cmd.papinho.moderation;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.contact.ContactService;
import io.github.labdev2025cmd.papinho.identity.UserDirectory;
import io.github.labdev2025cmd.papinho.identity.UserDirectory.UserSummary;
import io.github.labdev2025cmd.papinho.messaging.MessageService;
import io.github.labdev2025cmd.papinho.shared.error.ApiException;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;

/**
 * Denúncias (CT-04): quem participa de uma conversa individual denuncia a outra pessoa; as
 * {@value #COPIED_MESSAGES} últimas mensagens da conversa vão para a fila de moderação, opcionalmente com
 * bloqueio. O admin resolve ou arquiva (AD-01).
 */
@Service
public class ReportService {

    /** Quantas mensagens da conversa são copiadas para a denúncia, como no WhatsApp. */
    static final int COPIED_MESSAGES = 5;

    private final ReportRepository reports;
    private final MessageService messages;
    private final ContactService contacts;
    private final UserDirectory users;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    ReportService(ReportRepository reports, MessageService messages, ContactService contacts, UserDirectory users,
            RateLimiter rateLimiter, Clock clock) {
        this.reports = reports;
        this.messages = messages;
        this.contacts = contacts;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** Denuncia a outra pessoa da conversa; com {@code block}, também a bloqueia. */
    @Transactional
    public void report(UUID me, UUID conversationId, @Nullable String reason, boolean block) {
        rateLimiter.consume(RateLimiter.Policy.REPORT_BY_USER, me.toString());
        MessageService.ReportSnapshot snapshot = messages.reportSnapshot(me, conversationId, COPIED_MESSAGES);
        List<Report.Copy> copies = snapshot.messages().stream()
                .map(m -> new Report.Copy(m.id(), m.senderId(), m.body(), m.sentAt())).toList();
        reports.save(new Report(me, snapshot.peerId(), conversationId, clean(reason), copies, clock.instant()));
        if (block) {
            contacts.block(me, snapshot.peerId());
        }
    }

    /** Fila do admin: abertas (da mais antiga) ou fechadas (da mais recente). */
    @Transactional(readOnly = true)
    public ReportPage list(boolean open, int page, int size) {
        PageRequest request = PageRequest.of(page, size);
        Page<Report> found = open ? reports.findByStatusOrderByCreatedAtAsc(Report.Status.OPEN, request)
                : reports.findByStatusNotOrderByCreatedAtDesc(Report.Status.OPEN, request);
        Set<UUID> ids = new HashSet<>();
        for (Report report : found) {
            ids.add(report.reportedId());
            if (report.reporterId() != null) {
                ids.add(report.reporterId());
            }
            report.messages().forEach(m -> {
                if (m.senderId() != null) {
                    ids.add(m.senderId());
                }
            });
        }
        Map<UUID, UserSummary> people = users.findAllById(ids).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        List<ReportView> items = found.getContent().stream().map(r -> toView(r, people)).toList();
        return new ReportPage(items, found.getNumber(), found.getTotalElements(), found.getTotalPages(),
                reports.countByStatus(Report.Status.OPEN));
    }

    /** Fecha a denúncia; {@code dismiss} arquiva sem ação. */
    @Transactional
    public void close(UUID adminId, UUID reportId, boolean dismiss) {
        Report report = reports.findById(reportId).orElseThrow(NotFoundException::new);
        if (report.status() != Report.Status.OPEN) {
            return;
        }
        report.close(dismiss ? Report.Status.DISMISSED : Report.Status.RESOLVED, adminId, clock.instant());
    }

    private static ReportView toView(Report report, Map<UUID, UserSummary> people) {
        UUID reporterId = report.reporterId();
        return new ReportView(report.id(), person(reporterId, people), person(report.reportedId(), people),
                report.reason(), report.status().name(), report.createdAt(), report.resolvedAt(),
                report.messages().stream().map(m -> new CopiedMessage(person(m.senderId(), people), m.body(),
                        m.sentAt(), report.reportedId().equals(m.senderId()))).toList());
    }

    private static @Nullable PersonRef person(@Nullable UUID id, Map<UUID, UserSummary> people) {
        if (id == null) {
            return null;
        }
        UserSummary summary = people.get(id);
        return summary == null ? null : new PersonRef(id, summary.username(), summary.active());
    }

    private static @Nullable String clean(@Nullable String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String value = reason.strip();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    /** Pessoa citada na denúncia; nula quando a conta já foi excluída. */
    public record PersonRef(UUID id, String username, boolean active) {
    }

    /** @param fromReported a mensagem foi escrita por quem foi denunciado */
    public record CopiedMessage(@Nullable PersonRef sender, String body, Instant sentAt, boolean fromReported) {
    }

    public record ReportView(UUID id, @Nullable PersonRef reporter, @Nullable PersonRef reported,
            @Nullable String reason, String status, Instant createdAt, @Nullable Instant resolvedAt,
            List<CopiedMessage> messages) {
    }

    /** @param openCount total de denúncias abertas (contador do painel) */
    public record ReportPage(List<ReportView> items, int page, long totalItems, int totalPages, long openCount) {
    }

    static class NotFoundException extends ApiException {
        NotFoundException() {
            super(HttpStatus.NOT_FOUND, "not-found", "Report not found");
        }
    }
}
