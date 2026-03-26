package com.amenbank.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends AmenBankException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
}
