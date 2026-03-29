package com.amenbank.service;

public interface EmailService {
    void sendEmailVerification(String to, String name, String token);
    void sendPasswordReset(String to, String name, String token);
    void sendPasswordChangedEmail(String to, String name);
    void send2FAEnabledEmail(String to, String name);
    void sendTransferNotification(String to, String name, String ref, java.math.BigDecimal amount, String currency);
    void sendCreditStatusUpdate(String to, String name, String creditType, String status, String reason);
    void sendKycStatusUpdate(String to, String name, String status, String reason);
    void notifyAdminsNewKycRequest(String userName, String userEmail);
    void notifyAdminsNewCreditApplication(String userName, String userEmail, String creditType, java.math.BigDecimal amount);
    void sendGeneric(String to, String subject, String body);

    // ─── Onboarding ───────────────────────────────────────────────────
    /** Notify admins a new registration request has arrived */
    void notifyAdminsNewRegistrationRequest(String applicantEmail);

    /** Send activation link email to new client */
    void sendAccountActivation(String to, String name, String activationToken);

    /** Notify client their registration was rejected */
    void sendRegistrationRejected(String to, String reason);

    /** Confirm account activation success */
    void sendAccountActivatedConfirmation(String to, String name);
}
