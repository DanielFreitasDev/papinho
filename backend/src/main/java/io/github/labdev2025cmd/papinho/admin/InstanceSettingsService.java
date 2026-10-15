package io.github.labdev2025cmd.papinho.admin;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leitura e alteração das configurações da instância (AD-02). */
@Service
public class InstanceSettingsService {

    private final InstanceSettingsRepository repository;
    private final Clock clock;

    InstanceSettingsService(InstanceSettingsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Configurações atuais; a linha única é criada pela migration. */
    @Transactional(readOnly = true)
    public InstanceSettings current() {
        return load().toView();
    }

    /** Altera as configurações; só o admin chega aqui (o controller exige {@code ROLE_ADMIN}). */
    @Transactional
    public InstanceSettings update(UpdateInstanceSettingsRequest request) {
        InstanceSettingsEntity entity = load();
        entity.update(request, clock.instant());
        return entity.toView();
    }

    private InstanceSettingsEntity load() {
        return repository.findById(InstanceSettingsEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("instance_settings row missing"));
    }
}
