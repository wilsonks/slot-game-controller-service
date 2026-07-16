package com.slotcentral.gamecontroller.exception;

public class GameEngineException extends RuntimeException {
    private final int statusCode;

    public GameEngineException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GameEngineException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 502;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
