package com.amenbank.exception;

public class InvalidTotpException extends BusinessException {
    public InvalidTotpException() {
        super("Invalid or expired TOTP code.", "INVALID_TOTP");
    }
}
