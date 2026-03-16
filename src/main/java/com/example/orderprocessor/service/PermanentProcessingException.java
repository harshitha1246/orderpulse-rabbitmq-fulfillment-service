package com.example.orderprocessor.service;

public class PermanentProcessingException extends RuntimeException {
    public PermanentProcessingException(String message) {
        super(message);
    }

    public PermanentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
