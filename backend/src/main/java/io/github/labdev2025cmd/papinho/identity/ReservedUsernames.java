package io.github.labdev2025cmd.papinho.identity;

import java.util.Set;

/**
 * Nomes que ninguém pode registrar (ID-01): papéis do sistema, rotas do app e nomes que facilitam golpes.
 * A comparação ignora pontos e sublinhados, para que {@code a.d.m.i.n} também seja recusado.
 */
final class ReservedUsernames {

    private static final Set<String> RESERVED = Set.of(
            "admin", "administrador", "administrator", "root", "system", "sistema", "papinho", "suporte", "support",
            "help", "ajuda", "moderador", "moderator", "moderacao", "staff", "equipe", "oficial", "official",
            "security", "seguranca", "everyone", "todos", "all", "here", "null", "undefined", "anonymous",
            "anonimo", "api", "ws", "setup", "login", "logout", "register", "cadastro", "settings",
            "configuracoes", "me", "eu", "voce", "you", "owner", "dono", "bot", "info", "contato", "contact",
            "noreply", "postmaster", "webmaster", "hostmaster", "abuse", "whatsapp", "telegram", "signal");

    private ReservedUsernames() {
    }

    static boolean isReserved(Username username) {
        String compact = username.value().replace(".", "").replace("_", "");
        return RESERVED.contains(username.value()) || RESERVED.contains(compact);
    }
}
