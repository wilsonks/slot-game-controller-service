package com.slotcentral.gamecontroller.exception;

public class BankServiceException extends RuntimeException {
    private final int statusCode;

    public BankServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public BankServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 502;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
