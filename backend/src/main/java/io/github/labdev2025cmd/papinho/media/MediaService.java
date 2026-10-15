package io.github.labdev2025cmd.papinho.media;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Consulta e leitura de arquivos enviados (MD-01, MD-02). Quem enviou sempre abre; os demais, quando alguma
 * {@link MediaAccessPolicy} permitir (o arquivo está numa conversa da pessoa).
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaFileRepository files;
    private final FileStorage storage;
    private final List<MediaAccessPolicy> policies;

    MediaService(MediaFileRepository files, FileStorage storage, List<MediaAccessPolicy> policies) {
        this.files = files;
        this.storage = storage;
        this.policies = policies;
    }

    /** Metadados para quem pode ver o arquivo; 404 para os demais (não revela que existe). */
    @Transactional(readOnly = true)
    public MediaView view(UUID viewer, UUID id) {
        return requireReadable(viewer, id).toView();
    }

    /** Arquivo que {@code owner} enviou e pode anexar a uma mensagem. */
    @Transactional(readOnly = true)
    public MediaView requireOwned(UUID owner, UUID id) {
        return files.findById(id).filter(f -> owner.equals(f.ownerId())).map(MediaFile::toView)
                .orElseThrow(MediaExceptions.NotAttachableException::new);
    }

    /** Metadados de vários arquivos de uma vez, sem checar acesso (quem chama já checou pela mensagem). */
    @Transactional(readOnly = true)
    public Map<UUID, MediaView> describe(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return files.findAllById(ids).stream().map(MediaFile::toView)
                .collect(Collectors.toMap(MediaView::id, Function.identity()));
    }

    /** Conteúdo (ou miniatura) para quem pode ver. */
    @Transactional(readOnly = true)
    public Content content(UUID viewer, UUID id, boolean thumbnail) throws IOException {
        MediaFile file = requireReadable(viewer, id);
        if (thumbnail && !file.hasThumbnail()) {
            throw new MediaExceptions.NotFoundException();
        }
        String key = thumbnail ? MediaFile.thumbnailKey(id) : MediaFile.contentKey(id);
        Resource resource = storage.resource(key).orElseThrow(MediaExceptions.NotFoundException::new);
        String type = thumbnail ? "image/jpeg" : file.contentType();
        return new Content(resource, type, file.filename(), ContentTypes.inline(type));
    }

    /**
     * Remove o arquivo do servidor (MS-06, MS-07, MS-08): os metadados na transação e o conteúdo depois do
     * commit. Quem chama garante que nenhuma mensagem ainda o usa.
     */
    @Transactional
    public void delete(UUID id) {
        files.findById(id).ifPresent(file -> {
            files.delete(file);
            Runnable removeContent = () -> {
                try {
                    storage.delete(MediaFile.contentKey(id));
                    storage.delete(MediaFile.thumbnailKey(id));
                } catch (IOException e) {
                    log.warn("Falha ao remover o conteúdo do arquivo {}", id, e);
                }
            };
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        removeContent.run();
                    }
                });
            } else {
                removeContent.run();
            }
        });
    }

    private MediaFile requireReadable(UUID viewer, UUID id) {
        MediaFile file = files.findById(id).orElseThrow(MediaExceptions.NotFoundException::new);
        if (viewer.equals(file.ownerId()) || policies.stream().anyMatch(p -> p.canRead(viewer, id))) {
            return file;
        }
        throw new MediaExceptions.NotFoundException();
    }

    /**
     * Conteúdo a enviar.
     *
     * @param inline o navegador pode exibir (imagem, vídeo ou áudio conhecidos); senão, sempre download
     */
    public record Content(Resource resource, String contentType, String filename, boolean inline) {
    }
}
