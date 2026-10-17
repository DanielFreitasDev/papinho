package io.github.labdev2025cmd.papinho.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/** Para os testes de outros módulos conferirem o arquivo no armazenamento, não só pela API. */
public final class StoredMedia {

    private StoredMedia() {
    }

    /** O conteúdo do anexo ainda está no armazenamento (disco ou S3). */
    public static boolean exists(FileStorage storage, String mediaId) {
        try {
            return storage.resource(MediaFile.contentKey(UUID.fromString(mediaId))).isPresent();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
