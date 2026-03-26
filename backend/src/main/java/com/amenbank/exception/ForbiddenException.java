package com.amenbank.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends AmenBankException {
    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
}
