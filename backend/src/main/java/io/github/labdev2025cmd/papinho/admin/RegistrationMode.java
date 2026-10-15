package io.github.labdev2025cmd.papinho.admin;

/** Modos de cadastro da instância (ID-07). O padrão é {@link #INVITE_ONLY}. */
public enum RegistrationMode {
    /** Qualquer pessoa que acesse a instância pode criar conta. */
    OPEN,
    /** Só com um convite válido. */
    INVITE_ONLY,
    /** Ninguém cria conta (exceto o primeiro usuário de uma instância vazia). */
    CLOSED
}
