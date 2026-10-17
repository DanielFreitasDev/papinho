package io.github.labdev2025cmd.papinho.media;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
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
    private final List<MediaReferences> references;

    MediaService(MediaFileRepository files, FileStorage storage, List<MediaAccessPolicy> policies,
            List<MediaReferences> references) {
        this.files = files;
        this.storage = storage;
        this.policies = policies;
        this.references = references;
    }

    /** Metadados para quem pode ver o arquivo; 404 para os demais (não revela que existe). */
    @Transactional(readOnly = true)
    public MediaView view(UUID viewer, UUID id) {
        return requireReadable(viewer, id, null).toView();
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
        return content(viewer, id, thumbnail, null);
    }

    /** Conteúdo com o grant de anexo de conversa trancada, quando a URL o traz (PV-03). */
    @Transactional(readOnly = true)
    public Content content(UUID viewer, UUID id, boolean thumbnail, @Nullable String grant) throws IOException {
        MediaFile file = requireReadable(viewer, id, grant);
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
        files.findById(id).ifPresent(this::remove);
    }

    /**
     * Confere até {@code limit} arquivos enviados antes de {@code cutoff} que ainda não foram conferidos (MD-01):
     * os que algum módulo usa ficam marcados e não voltam a ser conferidos; os que ninguém usa (upload que nunca
     * virou anexo, figurinha ou foto de grupo) saem do servidor e devolvem a cota. Devolve quantos conferiu.
     */
    @Transactional
    public int purgeUnused(Instant cutoff, int limit) {
        if (references.isEmpty()) {
            // Sem nenhum módulo que informe o uso, não há como saber o que está livre: não apaga nada.
            return 0;
        }
        List<UUID> candidates = files.lockUnchecked(cutoff, limit);
        if (candidates.isEmpty()) {
            return 0;
        }
        Set<UUID> used = new HashSet<>();
        references.forEach(module -> used.addAll(module.inUse(candidates)));
        if (!used.isEmpty()) {
            files.markInUse(used);
        }
        List<UUID> unused = candidates.stream().filter(id -> !used.contains(id)).toList();
        files.findAllById(unused).forEach(this::remove);
        return candidates.size();
    }

    private void remove(MediaFile file) {
        UUID id = file.id();
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
    }

    /**
     * Conteúdo para um acesso já autorizado pelo módulo dono do arquivo (token de visualização única, MS-08), sem
     * passar pelas políticas.
     */
    @Transactional(readOnly = true)
    public Content grantedContent(UUID id) throws IOException {
        MediaFile file = files.findById(id).orElseThrow(MediaExceptions.NotFoundException::new);
        Resource resource = storage.resource(MediaFile.contentKey(id))
                .orElseThrow(MediaExceptions.NotFoundException::new);
        return new Content(resource, file.contentType(), file.filename(), ContentTypes.inline(file.contentType()));
    }

    /** Arquivos que a pessoa enviou, para a exportação dos dados dela (BK-02). */
    @Transactional(readOnly = true)
    public java.util.List<OwnedFile> ownedBy(UUID ownerId) {
        java.util.List<MediaFile> owned = files.findByOwner(ownerId);
        java.util.Set<UUID> hidden = hiddenFromOwner(ownerId, owned.stream().map(MediaFile::id).toList());
        return owned.stream().filter(file -> !hidden.contains(file.id()))
                .map(file -> new OwnedFile(file.id(), file.filename())).toList();
    }

    /** Arquivos da pessoa que a exportação desta requisição não leva (conversas trancadas fechadas, PV-03). */
    java.util.Set<UUID> hiddenFromOwner(UUID ownerId, java.util.Collection<UUID> mediaIds) {
        java.util.Set<UUID> hidden = new java.util.HashSet<>();
        if (!mediaIds.isEmpty()) {
            policies.forEach(policy -> hidden.addAll(policy.hiddenFromOwner(ownerId, mediaIds)));
        }
        return hidden;
    }

    /** Arquivo de uma pessoa: id e nome original já limpo. */
    public record OwnedFile(UUID id, String filename) {
    }

    private MediaFile requireReadable(UUID viewer, UUID id, @Nullable String grant) {
        MediaFile file = files.findById(id).orElseThrow(MediaExceptions.NotFoundException::new);
        if (policies.stream().anyMatch(p -> p.restricted(id) || p.lockedWithoutGrant(viewer, id, grant))) {
            throw new MediaExceptions.NotFoundException();
        }
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
