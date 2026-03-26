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
    void sendGeneric(String to, String subject, String body);
}
