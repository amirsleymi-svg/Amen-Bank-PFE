package com.amenbank.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class AmenBankException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AmenBankException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public AmenBankException(String message, HttpStatus status) {
        this(message, status, status.name());
    }
}
