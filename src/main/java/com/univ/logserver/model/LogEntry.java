package com.univ.logserver.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Représente une entrée de log avec toutes ses métadonnées
 * Inclut la sérialisation/désérialisation des données
 */
public class LogEntry {
    private final String id;
    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String message;
    private final String applicationName;
    private final String hostname;
    private final Map<String, String> metadata;
    
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Constructeur complet pour une entrée de log
     */
    public LogEntry(LogLevel level, String message, String applicationName, 
                   String hostname, Map<String, String> metadata) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.level = level;
        this.message = message;
        this.applicationName = applicationName;
        this.hostname = hostname;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    /**
     * Constructeur simplifié
     */
    public LogEntry(LogLevel level, String message, String applicationName) {
        this(level, message, applicationName, "unknown", null);
    }
    
    // Getters
    public String getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public LogLevel getLevel() { return level; }
    public String getMessage() { return message; }
    public String getApplicationName() { return applicationName; }
    public String getHostname() { return hostname; }
    public Map<String, String> getMetadata() { return new HashMap<>(metadata); }
    
    /**
     * Ajoute une métadonnée à l'entrée de log
     */
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }
    
    /**
     * Sérialise l'entrée de log en format JSON simple
     */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(id).append("\",");
        json.append("\"timestamp\":\"").append(timestamp.format(FORMATTER)).append("\",");
        json.append("\"level\":\"").append(level.getName()).append("\",");
        json.append("\"message\":\"").append(escapeJson(message)).append("\",");
        json.append("\"application\":\"").append(applicationName).append("\",");
        json.append("\"hostname\":\"").append(hostname).append("\",");
        json.append("\"metadata\":{");
        
        boolean first = true;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"")
                .append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        
        json.append("}}");
        return json.toString();
    }
    
    /**
     * Échappe les caractères JSON
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
    
    /**
     * Format lisible pour l'affichage
     */
    public String toFormattedString() {
        return String.format("[%s] %s [%s] %s - %s", 
                           timestamp.format(FORMATTER),
                           level.getName(),
                           applicationName,
                           hostname,
                           message);
    }
    
    @Override
    public String toString() {
        return toFormattedString();
    }
}