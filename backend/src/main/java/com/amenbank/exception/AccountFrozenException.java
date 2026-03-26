package com.amenbank.exception;

public class AccountFrozenException extends BusinessException {
    public AccountFrozenException() {
        super("This account is frozen and cannot perform transactions.", "ACCOUNT_FROZEN");
    }
}
