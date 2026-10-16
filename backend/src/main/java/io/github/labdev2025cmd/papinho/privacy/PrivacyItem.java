package io.github.labdev2025cmd.papinho.privacy;

/** Itens com visibilidade configurável e lista "exceto…" própria. */
public enum PrivacyItem {
    LAST_SEEN("last_seen"), ONLINE("online"), PHOTO("photo"), ABOUT("about"), GROUPS_ADD("groups_add");

    private final String column;

    PrivacyItem(String column) {
        this.column = column;
    }

    String column() {
        return column;
    }
}
