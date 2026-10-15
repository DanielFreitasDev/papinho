package io.github.labdev2025cmd.papinho.identity;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;

/**
 * Lista as passkeys do usuário (ID-04). Registrar e remover usam os endpoints do Spring Security
 * ({@code POST /webauthn/register/options}, {@code POST /webauthn/register}, {@code DELETE /webauthn/register/{id}}),
 * que já conferem o dono da credencial.
 */
@RestController
@RequestMapping("/api/v1/account/passkeys")
class PasskeyController {

    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final UserCredentialRepository credentials;

    PasskeyController(PublicKeyCredentialUserEntityRepository userEntities, UserCredentialRepository credentials) {
        this.userEntities = userEntities;
        this.credentials = credentials;
    }

    @GetMapping
    List<PasskeyView> list() {
        PublicKeyCredentialUserEntity entity = userEntities.findByUsername(CurrentUser.id().toString());
        if (entity == null) {
            return List.of();
        }
        return credentials.findByUserId(entity.getId()).stream()
                .map(record -> new PasskeyView(record.getCredentialId().toBase64UrlString(), record.getLabel(),
                        record.getCreated(), record.getLastUsed()))
                .toList();
    }

    /** Passkey registrada; {@code id} é o usado em {@code DELETE /webauthn/register/{id}}. */
    record PasskeyView(String id, String label, @Nullable Instant createdAt, @Nullable Instant lastUsedAt) {
    }
}
