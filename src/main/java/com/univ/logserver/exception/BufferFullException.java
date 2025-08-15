package com.univ.logserver.exception;

public class BufferFullException extends Exception {
    public BufferFullException(String message) {
        super(message);
    }
    
    public BufferFullException(String message, Throwable cause) {
        super(message, cause);
    }
}