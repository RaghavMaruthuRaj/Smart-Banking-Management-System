package com.smartbanking.common.exception;

public class InvalidTransactionException extends BankException {
    public InvalidTransactionException(String message) {
        super(message);
    }
}
