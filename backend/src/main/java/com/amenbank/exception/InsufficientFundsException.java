package com.amenbank.exception;

public class InsufficientFundsException extends BusinessException {
    public InsufficientFundsException() {
        super("Insufficient funds to complete this transaction.", "INSUFFICIENT_FUNDS");
    }
}
