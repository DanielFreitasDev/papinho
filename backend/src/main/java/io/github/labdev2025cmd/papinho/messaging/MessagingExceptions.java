package io.github.labdev2025cmd.papinho.messaging;

import org.springframework.http.HttpStatus;

import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/** Erros do módulo de mensagens, em Problem Details. */
final class MessagingExceptions {

    private MessagingExceptions() {
    }

    /** Conversa ou mensagem inexistente ou fora do alcance de quem pediu (mesma resposta, contra IDOR). */
    static class NotFoundException extends ApiException {
        NotFoundException() {
            super(HttpStatus.NOT_FOUND, "conversation-not-found", "Conversation or message not found");
        }
    }

    /** Há bloqueio entre as duas pessoas (CT-03). */
    static class BlockedException extends ApiException {
        BlockedException() {
            super(HttpStatus.FORBIDDEN, "message-blocked", "Messaging blocked between these users");
        }
    }

    static class SelfConversationException extends ApiException {
        SelfConversationException() {
            super(HttpStatus.BAD_REQUEST, "conversation-self", "Cannot open a direct conversation with yourself");
        }
    }

    static class InvalidMessageException extends ApiException {
        InvalidMessageException(String reason) {
            super(HttpStatus.BAD_REQUEST, "message-invalid", "Invalid message: " + reason);
        }
    }

    /** O mesmo {@code clientMessageId} já foi usado em outra conversa. */
    static class ClientMessageIdConflictException extends ApiException {
        ClientMessageIdConflictException() {
            super(HttpStatus.CONFLICT, "message-conflict", "clientMessageId already used in another conversation");
        }
    }

    /** Só quem enviou edita ou apaga para todos (MS-05, MS-06). */
    static class NotAuthorException extends ApiException {
        NotAuthorException() {
            super(HttpStatus.FORBIDDEN, "message-not-author", "Only the sender can change this message");
        }
    }

    /** Passou a janela configurada pelo admin para editar ou apagar para todos. */
    static class WindowExpiredException extends ApiException {
        WindowExpiredException() {
            super(HttpStatus.CONFLICT, "message-window-expired", "The time window for this change has expired");
        }
    }

    /** Grupo com "só admins enviam" (ou ação reservada a admins) e quem pediu não é admin (GR-01). */
    static class OnlyAdminsException extends ApiException {
        OnlyAdminsException() {
            super(HttpStatus.FORBIDDEN, "group-only-admins", "Only group admins can do this");
        }
    }

    /**
     * Não dá para adicionar esta pessoa ao grupo (bloqueio, privacidade ou não é contato); a mesma resposta para
     * todos os motivos, sem revelar qual.
     */
    static class CannotAddException extends ApiException {
        CannotAddException() {
            super(HttpStatus.FORBIDDEN, "group-cannot-add", "This person cannot be added to the group");
        }
    }

    static class InvalidGroupException extends ApiException {
        InvalidGroupException(String reason) {
            super(HttpStatus.BAD_REQUEST, "group-invalid", "Invalid group: " + reason);
        }
    }

    /** Link de convite do grupo inexistente ou revogado (GR-02). */
    static class InviteNotFoundException extends ApiException {
        InviteNotFoundException() {
            super(HttpStatus.NOT_FOUND, "group-invite-not-found", "Group invite not found");
        }
    }

    /** Enquete encerrada: não aceita mais votos (MS-09). */
    static class PollClosedException extends ApiException {
        PollClosedException() {
            super(HttpStatus.CONFLICT, "poll-closed", "This poll is closed");
        }
    }

    /** Visualização única já aberta por esta pessoa ou arquivo já removido (MS-08). */
    static class ViewOnceGoneException extends ApiException {
        ViewOnceGoneException() {
            super(HttpStatus.GONE, "view-once-gone", "This view once message was already opened");
        }
    }

    /** Lista de transmissão que já passou da data de expiração (GR-04). */
    static class BroadcastExpiredException extends ApiException {
        BroadcastExpiredException() {
            super(HttpStatus.FORBIDDEN, "broadcast-expired", "This broadcast list has expired");
        }
    }

    /** GIFs desligados na instância (MS-10). */
    static class GifsDisabledException extends ApiException {
        GifsDisabledException() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "gifs-disabled", "GIFs are disabled on this instance");
        }
    }

    /** O provedor de GIF falhou ou mandou algo fora dos limites. */
    static class GifUnavailableException extends ApiException {
        GifUnavailableException() {
            super(HttpStatus.BAD_GATEWAY, "gif-unavailable", "The GIF provider is unavailable");
        }
    }
}
