package io.github.labdev2025cmd.papinho.moderation;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findByStatusOrderByCreatedAtAsc(Report.Status status, Pageable pageable);

    Page<Report> findByStatusNotOrderByCreatedAtDesc(Report.Status status, Pageable pageable);

    long countByStatus(Report.Status status);
}
