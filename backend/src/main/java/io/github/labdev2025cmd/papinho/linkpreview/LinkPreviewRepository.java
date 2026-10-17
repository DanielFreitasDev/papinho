package io.github.labdev2025cmd.papinho.linkpreview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface LinkPreviewRepository extends JpaRepository<LinkPreview, String> {

    /** Apaga as prévias cujo endereço aparece em {@code text}. */
    @Modifying
    @Query(value = "delete from link_previews where position(url in :text) > 0", nativeQuery = true)
    int deleteMentionedIn(String text);
}
