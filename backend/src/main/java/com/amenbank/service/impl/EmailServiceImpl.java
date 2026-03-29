package com.amenbank.service.impl;

import com.amenbank.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail-from:noreply@amenbank.com}")
    private String mailFrom;

    @Value("${app.mail-from-name:Amen Bank}")
    private String mailFromName;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    // ─── Email verification ───────────────────────────────────────
    @Async
    @Override
    public void sendEmailVerification(String to, String name, String token) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("name", name);
        ctx.setVariable("verifyUrl", frontendUrl + "/auth/verify-email?token=" + token);
        ctx.setVariable("appName", "Amen Bank");
        sendHtml(to, "Vérifiez votre adresse email — Amen Bank", "email/verify-email", ctx);
    }

    // ─── Password reset ───────────────────────────────────────────
    @Async
    @Override
    public void sendPasswordReset(String to, String name, String token) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("name", name);
        ctx.setVariable("resetUrl", frontendUrl + "/auth/reset-password?token=" + token);
        ctx.setVariable("appName", "Amen Bank");
        sendHtml(to, "Réinitialisation de votre mot de passe — Amen Bank", "email/reset-password", ctx);
    }

    // ─── Password changed ─────────────────────────────────────────
    @Async
    @Override
    public void sendPasswordChangedEmail(String to, String name) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("name", name);
        ctx.setVariable("appName", "Amen Bank");
        sendHtml(to, "Mot de passe modifié — Amen Bank", "email/password-changed", ctx);
    }

    // ─── 2FA enabled ──────────────────────────────────────────────
    @Async
    @Override
    public void send2FAEnabledEmail(String to, String name) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("name", name);
        sendHtml(to, "Double authentification activée — Amen Bank", "email/2fa-enabled", ctx);
    }

    // ─── Transfer notification ────────────────────────────────────
    @Async
    @Override
    public void sendTransferNotification(String to, String name, String ref,
                                          BigDecimal amount, String currency) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("name", name);
        ctx.setVariable("ref", ref);
        ctx.setVariable("amount", String.format("%.3f", amount));
        ctx.setVariable("currency", currency);
        sendHtml(to, "Confirmation de virement — Amen Bank", "email/transfer-confirm", ctx);
    }

    // ─── Credit status ────────────────────────────────────────────
    @Async
    @Override
    public void sendCreditStatusUpdate(String to, String name, String creditType,
                                        String status, String reason) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("name", name);
        ctx.setVariable("creditType", creditType);
        ctx.setVariable("status", status);
        ctx.setVariable("reason", reason);
        ctx.setVariable("dashboardUrl", frontendUrl + "/dashboard/credits");
        String subject = "APPROVED".equals(status)
                ? "Votre demande de crédit a été approuvée — Amen Bank"
                : "Mise à jour de votre demande de crédit — Amen Bank";
        sendHtml(to, subject, "email/credit-status", ctx);
    }

    // ─── KYC status ───────────────────────────────────────────────
    @Async
    @Override
    public void sendKycStatusUpdate(String to, String name, String status, String reason) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("name", name);
        ctx.setVariable("status", status);
        ctx.setVariable("reason", reason);
        ctx.setVariable("loginUrl", frontendUrl + "/auth/login");
        String subject = "APPROVED".equals(status)
                ? "Bienvenue chez Amen Bank — Votre compte est activé !"
                : "Mise à jour de votre demande d'ouverture de compte — Amen Bank";
        sendHtml(to, subject, "email/kyc-status", ctx);
    }

    // ─── Notify admins ────────────────────────────────────────────
    @Async
    @Override
    public void notifyAdminsNewKycRequest(String userName, String userEmail) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("userName", userName);
        ctx.setVariable("userEmail", userEmail);
        ctx.setVariable("adminUrl", frontendUrl + "/admin");
        // In production: fetch admin emails from DB
        String adminEmail = "admin@amenbank.com";
        sendHtml(adminEmail, "Nouvelle demande KYC — " + userName, "email/admin-new-kyc", ctx);
    }

    // ─── Onboarding ───────────────────────────────────────────────
    @Async
    @Override
    public void notifyAdminsNewCreditApplication(String userName, String userEmail, String creditType, BigDecimal amount) {
        String adminEmail = "admin@amenbank.com";
        String subject = "New credit application - " + userName;
        String body = "A new credit application was submitted.\n"
                + "User: " + userName + "\n"
                + "Email: " + userEmail + "\n"
                + "Type: " + creditType + "\n"
                + "Amount: " + amount + " TND\n"
                + "Review URL: " + frontendUrl + "/admin/credits";
        sendGeneric(adminEmail, subject, body);
    }

    @Async
    @Override
    public void notifyAdminsNewRegistrationRequest(String applicantEmail) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("applicantEmail", applicantEmail);
        ctx.setVariable("adminUrl", frontendUrl + "/admin");
        String adminEmail = "admin@amenbank.com";
        sendHtml(adminEmail, "Nouvelle demande d'inscription — " + applicantEmail, "email/admin-new-registration", ctx);
    }

    @Async
    @Override
    public void sendAccountActivation(String to, String name, String activationToken) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("name", name);
        ctx.setVariable("activationUrl", frontendUrl + "/auth/activate?token=" + activationToken);
        ctx.setVariable("appName", "Amen Bank");
        sendHtml(to, "Activez votre compte Amen Bank", "email/account-activation", ctx);
    }

    @Async
    @Override
    public void sendRegistrationRejected(String to, String reason) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("reason", reason);
        ctx.setVariable("appName", "Amen Bank");
        sendHtml(to, "Mise à jour de votre demande d'inscription — Amen Bank", "email/registration-rejected", ctx);
    }

    @Async
    @Override
    public void sendAccountActivatedConfirmation(String to, String name) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariable("name", name);
        ctx.setVariable("loginUrl", frontendUrl + "/auth/login");
        ctx.setVariable("appName", "Amen Bank");
        sendHtml(to, "Bienvenue chez Amen Bank — Compte activé !", "email/account-activated", ctx);
    }

    // ─── Generic ──────────────────────────────────────────────────
    @Async
    @Override
    public void sendGeneric(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailFrom, mailFromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send generic email to {}: {}", to, e.getMessage());
        }
    }

    // ─── Internal HTML sender ─────────────────────────────────────
    private void sendHtml(String to, String subject, String template, Context ctx) {
        try {
            String html = templateEngine.process(template, ctx);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom, mailFromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent: [{}] → {}", subject, to);
        } catch (Exception e) {
            log.error("Failed to send email [{}] to {}: {}", subject, to, e.getMessage());
        }
    }
}
