package com.worksphere.common.mail;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends the account emails (spec section 5: invitations, verification, password reset).
 *
 * <ul>
 *   <li>Mail is sent only AFTER the database transaction commits, so nobody receives a link for an
 *       invitation or reset token that was rolled back.
 *   <li>Mail is sent on a separate thread, so a slow SMTP server never slows an API request.
 *   <li>A failed send is logged and swallowed: the request already succeeded, and the user can ask
 *       again ("resend"). Mail problems must not become API outages.
 *   <li>Links point at the frontend ({@code app.mail.frontend-url}); the raw token is only ever in
 *       the email, never stored (the database keeps just its hash).
 * </ul>
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties properties;
    private final Executor executor;

    public EmailService(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(500);
        pool.setThreadNamePrefix("mail-");
        pool.initialize();
        this.executor = pool;
    }

    public void sendInvitation(String to, String organizationName, String rawToken) {
        logTokenForDevelopment("Invitation token for " + to, rawToken);
        send(to, "You have been invited to " + organizationName + " on WorkSphere",
                "You were invited to join " + organizationName + " on WorkSphere.\n\n"
                        + "Accept the invitation: " + link("/accept-invitation", rawToken) + "\n\n"
                        + "If you were not expecting this, you can ignore this email.");
    }

    public void sendPasswordReset(String to, String rawToken) {
        logTokenForDevelopment("Password reset token for " + to, rawToken);
        send(to, "Reset your WorkSphere password",
                "Someone asked to reset the password for this account.\n\n"
                        + "Choose a new password: " + link("/reset-password", rawToken) + "\n\n"
                        + "If this was not you, ignore this email; your password has not changed.");
    }

    public void sendEmailVerification(String to, String rawToken) {
        logTokenForDevelopment("Email verification token for " + to, rawToken);
        send(to, "Verify your WorkSphere email address",
                "Welcome to WorkSphere!\n\nConfirm your email address: " + link("/verify-email", rawToken));
    }

    /** Queues a message for after the current transaction commits (immediately if there is none). */
    void send(String to, String subject, String body) {
        Runnable deliver = () -> executor.execute(() -> sendNow(to, subject, body));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver.run();
                }
            });
        } else {
            deliver.run();
        }
    }

    private void sendNow(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (RuntimeException e) {
            log.warn("Could not send email '{}' to a recipient: {}", subject, e.getMessage());
        }
    }

    private String link(String path, String rawToken) {
        return properties.frontendUrl() + path + "?token=" + rawToken;
    }

    /**
     * Raw tokens are credentials and must not normally be logged. This exists only for local
     * development and the automated tests (which read the token from the log); it is OFF unless
     * {@code app.mail.log-tokens=true}.
     */
    private void logTokenForDevelopment(String what, String rawToken) {
        if (properties.logTokens()) {
            log.info("{}: {} (dev log only)", what, rawToken);
        }
    }
}
