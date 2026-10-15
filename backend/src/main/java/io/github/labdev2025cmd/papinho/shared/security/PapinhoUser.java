package io.github.labdev2025cmd.papinho.shared.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal autenticado guardado na sessão (Spring Session JDBC serializa o contexto de segurança).
 *
 * <p>{@link #getUsername()} devolve o <b>id</b> do usuário, não o {@code @usuario}: o nome pode mudar
 * (ID-03), e o Spring Session indexa as sessões pelo nome do principal para listar e revogar (ID-06).</p>
 */
public final class PapinhoUser implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String handle;
    private final boolean admin;
    private final boolean enabled;
    private @Nullable String passwordHash;

    public PapinhoUser(UUID id, String handle, boolean admin, boolean enabled, @Nullable String passwordHash) {
        this.id = id;
        this.handle = handle;
        this.admin = admin;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    public UUID id() {
        return id;
    }

    /** {@code @usuario} no momento do login; para exibição use sempre o valor atual do banco. */
    public String handle() {
        return handle;
    }

    public boolean admin() {
        return admin;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return admin ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return id.toString();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String toString() {
        return "PapinhoUser[id=" + id + ", admin=" + admin + "]";
    }
}
