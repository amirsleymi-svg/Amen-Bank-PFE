package com.amenbank.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends AmenBankException {
    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }
}
