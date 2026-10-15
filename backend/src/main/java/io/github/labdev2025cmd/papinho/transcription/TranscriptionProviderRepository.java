package io.github.labdev2025cmd.papinho.transcription;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TranscriptionProviderRepository extends JpaRepository<TranscriptionProvider, UUID> {

    List<TranscriptionProvider> findAllByOrderByCreatedAt();
}
