package com.univ.logserver.exception;

/**
 * Exception pour les erreurs de parsing
 */
public class LogParseException extends Exception {
    public LogParseException(String message) {
        super(message);
    }
    public LogParseException(String message, Throwable cause) {
        super(message, cause);
    }
}