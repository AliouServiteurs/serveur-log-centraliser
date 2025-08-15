package com.univ.logserver.storage;

import com.univ.logserver.model.LogEntry;
import com.univ.logserver.model.LogLevel;
import java.util.List;

/**
 * Interface pour le stockage des logs
 * Permet différentes implémentations (fichier, base de données, etc.)
 */
public interface LogStorage {
    
    /**
     * Stocke une entrée de log
     */
    void store(LogEntry entry);
    
    /**
     * Stocke plusieurs entrées (batch - plus efficace)
     */
    void storeBatch(List<LogEntry> entries);
    
    /**
     * Récupère les logs par application
     */
    List<LogEntry> getLogsByApplication(String applicationName, int limit);
    
    /**
     * Récupère les logs par niveau
     */
    List<LogEntry> getLogsByLevel(LogLevel level, int limit);
    
    /**
     * Ferme les ressources de stockage
     */
    void close();
    
    /**
     * Statistiques de stockage
     */
    String getStorageStats();
}