package com.univ.logserver.processor;

import com.univ.logserver.model.LogEntry;
import com.univ.logserver.model.LogLevel;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser pour analyser les messages de logs entrants
 * Extrait les informations et métadonnées des logs bruts
 */
public class LogParser {
    
    /**
     * Parse un message de log brut en LogEntry
     * Format attendu: LEVEL|APPLICATION|HOSTNAME|MESSAGE|metadata_key=value,key2=value2
     */
    public static LogEntry parseLogMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return null;
        }
        
        try {
            String[] parts = rawMessage.split("\\|", 5);
            
            // Validation du format minimum
            if (parts.length < 4) {
                // Format simple: level message
                String[] simpleParts = rawMessage.split(" ", 2);
                if (simpleParts.length >= 2) {
                    LogLevel level = LogLevel.fromString(simpleParts[0]);
                    return new LogEntry(level, simpleParts[1], "unknown");
                }
                return new LogEntry(LogLevel.INFO, rawMessage, "unknown");
            }
            
            LogLevel level = LogLevel.fromString(parts[0].trim());
            String application = parts[1].trim();
            String hostname = parts[2].trim();
            String message = parts[3].trim();
            
            Map<String, String> metadata = new HashMap<>();
            
            // Parser les métadonnées si présentes
            if (parts.length > 4 && !parts[4].trim().isEmpty()) {
                String[] metadataPairs = parts[4].split(",");
                for (String pair : metadataPairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        metadata.put(keyValue[0].trim(), keyValue[1].trim());
                    }
                }
            }
            
            LogEntry entry = new LogEntry(level, message, application, hostname, metadata);
            
            // Ajouter des métadonnées automatiques
            entry.addMetadata("raw_length", String.valueOf(rawMessage.length()));
            entry.addMetadata("parsed_at", String.valueOf(System.currentTimeMillis()));
            
            return entry;
            
        } catch (Exception e) {
            System.err.println("Erreur parsing message: " + e.getMessage());
            // Retourner une entrée par défaut plutôt que null
            return new LogEntry(LogLevel.ERROR, 
                               "PARSE_ERROR: " + rawMessage, 
                               "unknown");
        }
    }
    
    /**
     * Valide si un message de log est bien formé
     */
    public static boolean isValidLogMessage(String message) {
        return message != null && 
               !message.trim().isEmpty() && 
               message.length() < 10000; // Limite de taille
    }
    
    /**
     * Enrichit une entrée de log avec des informations supplémentaires
     */
    public static void enrichLogEntry(LogEntry entry, String clientAddress) {
        if (entry != null) {
            entry.addMetadata("client_ip", clientAddress);
            entry.addMetadata("server_time", String.valueOf(System.currentTimeMillis()));
            
            // Classification automatique basée sur le message
            String message = entry.getMessage().toLowerCase();
            if (message.contains("error") || message.contains("exception")) {
                entry.addMetadata("category", "error");
            } else if (message.contains("warning") || message.contains("warn")) {
                entry.addMetadata("category", "warning");
            } else if (message.contains("startup") || message.contains("shutdown")) {
                entry.addMetadata("category", "lifecycle");
            } else {
                entry.addMetadata("category", "general");
            }
        }
    }
}