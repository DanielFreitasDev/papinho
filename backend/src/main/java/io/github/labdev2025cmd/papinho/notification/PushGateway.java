package io.github.labdev2025cmd.papinho.notification;

/** Entrega de um push cifrado a um serviço de push (porta; a implementação real é {@link WebPushGateway}). */
public interface PushGateway {

    Result send(PushSubscriptions.Subscription subscription, byte[] payload);

    /** {@code GONE}: o serviço disse que a assinatura acabou (404/410) e ela deve ser apagada. */
    enum Result {
        DELIVERED, GONE, FAILED
    }
}
