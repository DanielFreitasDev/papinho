package io.github.labdev2025cmd.papinho.media;

import java.util.Map;

import org.springframework.http.HttpStatus;

import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/** Erros de upload e mídia, todos como Problem Details. */
final class MediaExceptions {

    private MediaExceptions() {
    }

    static class NotFoundException extends ApiException {
        NotFoundException() {
            super(HttpStatus.NOT_FOUND, "not-found", "Media or upload not found");
        }
    }

    static class TooLargeException extends ApiException {
        TooLargeException() {
            super(HttpStatus.CONTENT_TOO_LARGE, "upload-too-large", "Upload larger than 1 GB");
        }
    }

    static class QuotaExceededException extends ApiException {
        QuotaExceededException(long availableBytes) {
            super(HttpStatus.CONTENT_TOO_LARGE, "quota-exceeded", Map.of("availableBytes", availableBytes),
                    "Storage quota exceeded");
        }
    }

    static class TusVersionException extends ApiException {
        TusVersionException() {
            super(HttpStatus.PRECONDITION_FAILED, "tus-version", "Unsupported Tus-Resumable version");
        }
    }

    static class InvalidUploadException extends ApiException {
        InvalidUploadException(String reason) {
            super(HttpStatus.BAD_REQUEST, "upload-invalid", "Invalid upload request: " + reason);
        }
    }

    static class OffsetConflictException extends ApiException {
        OffsetConflictException(long offset) {
            super(HttpStatus.CONFLICT, "upload-offset", Map.of("offset", offset), "Upload-Offset mismatch");
        }
    }

    static class UploadBusyException extends ApiException {
        UploadBusyException() {
            super(HttpStatus.LOCKED, "upload-busy", "Another request is writing this upload");
        }
    }

    /** Foto cujos metadados (EXIF, localização) o servidor não consegue tirar. */
    static class PhotoMetadataException extends ApiException {
        PhotoMetadataException() {
            super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "photo-metadata", "Photo metadata cannot be removed");
        }
    }

    static class MalwareException extends ApiException {
        MalwareException() {
            super(HttpStatus.UNPROCESSABLE_CONTENT, "media-malware", "File rejected by antivirus");
        }
    }

    /** O antivírus ligado não respondeu (MD-06): nada é aceito sem a análise. */
    static class ScannerUnavailableException extends ApiException {
        ScannerUnavailableException() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "media-scanner-unavailable", "Antivirus unavailable");
        }
    }

    static class NotAttachableException extends ApiException {
        NotAttachableException() {
            super(HttpStatus.BAD_REQUEST, "media-not-attachable", "Media not owned by sender");
        }
    }
}
