package io.github.labdev2025cmd.papinho.identity;

import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envia os e-mails de recuperação (ID-05) depois do commit e fora da requisição: a resposta não espera o SMTP,
 * então o tempo não revela se a conta existe nem se tem e-mail. Nada do endereço nem do link vai para os logs.
 *
 * <p>Não usa o registro de eventos do Modulith de propósito: o link com o token ficaria gravado no banco.</p>
 */
@Component
class RecoveryMailer {

    private static final Logger log = LoggerFactory.getLogger(RecoveryMailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final MailProperties properties;
    private final MessageSource messages;
    private final TaskExecutor executor;

    RecoveryMailer(ObjectProvider<JavaMailSender> sender, MailProperties properties, MessageSource messages) {
        this.sender = sender;
        this.properties = properties;
        this.messages = messages;
        SimpleAsyncTaskExecutor virtualThreads = new SimpleAsyncTaskExecutor("recovery-mail-");
        virtualThreads.setVirtualThreads(true);
        this.executor = virtualThreads;
    }

    /**
     * Agenda uma mensagem com os textos {@code mail.<kind>.subject} e {@code mail.<kind>.body}.
     *
     * @param userId só para o log, que não leva o endereço
     */
    void sendAfterCommit(UUID userId, String kind, String to, Locale locale, Object... arguments) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(messages.getMessage("mail." + kind + ".subject", null, locale));
        message.setText(messages.getMessage("mail." + kind + ".body", arguments, locale));
        Runnable send = () -> executor.execute(() -> send(userId, kind, message));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void send(UUID userId, String kind, SimpleMailMessage message) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            log.warn("Recovery mail not sent: no SMTP server configured kind={} userId={}", kind, userId);
            return;
        }
        try {
            mail.send(message);
            log.info("Recovery mail sent kind={} userId={}", kind, userId);
        } catch (MailException exception) {
            log.warn("Recovery mail failed kind={} userId={} cause={}", kind, userId,
                    exception.getClass().getSimpleName());
        }
    }
}
