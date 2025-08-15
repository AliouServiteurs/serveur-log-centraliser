package com.univ.logserver.model;


/**
 * Énumération des niveaux de log
 * Définit la priorité et la sévérité des messages de log
 */
public enum LogLevel {
    TRACE(1, "TRACE"),
    DEBUG(2, "DEBUG"),
    INFO(3, "INFO"),
    WARN(4, "WARN"),
    ERROR(5, "ERROR"),
    FATAL(6, "FATAL");
    
    private final int priority;
    private final String name;
    
    LogLevel(int priority, String name) {
        this.priority = priority;
        this.name = name;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * Convertit une chaîne en LogLevel
     */
    public static LogLevel fromString(String level) {
        try {
            return LogLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO; // Niveau par défaut
        }
    }
}