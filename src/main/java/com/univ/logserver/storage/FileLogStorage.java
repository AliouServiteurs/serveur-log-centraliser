package com.univ.logserver.storage;

import com.univ.logserver.model.LogEntry;
import com.univ.logserver.model.LogLevel;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Stockage des logs dans des fichiers
 * Rotation quotidienne des fichiers par application
 */
public class FileLogStorage implements LogStorage {
    private final String baseDirectory;
    private final Map<String, PrintWriter> writers = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    // Statistiques
    private long totalLogsStored = 0;
    private long totalBytesWritten = 0;
    
    public FileLogStorage(String baseDirectory) {
        this.baseDirectory = baseDirectory;
        createDirectoryIfNotExists();
    }
    
    private void createDirectoryIfNotExists() {
        try {
            Path path = Paths.get(baseDirectory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("Répertoire logs créé: " + baseDirectory);
            }
        } catch (IOException e) {
            System.err.println("Erreur création répertoire: " + e.getMessage());
        }
    }
    
    @Override
    public void store(LogEntry entry) {
        lock.readLock().lock();
        try {
            PrintWriter writer = getWriter(entry.getApplicationName());
            if (writer != null) {
                String logLine = formatLogEntry(entry);
                writer.println(logLine);
                writer.flush();
                
                totalLogsStored++;
                totalBytesWritten += logLine.length();
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void storeBatch(List<LogEntry> entries) {
        if (entries.isEmpty()) return;
        
        lock.readLock().lock();
        try {
            // Grouper par application pour optimiser l'écriture
            Map<String, List<LogEntry>> byApplication = new HashMap<>();
            for (LogEntry entry : entries) {
                byApplication.computeIfAbsent(entry.getApplicationName(), 
                    k -> new ArrayList<>()).add(entry);
            }
            
            // Écrire par lot pour chaque application
            for (Map.Entry<String, List<LogEntry>> appEntry : byApplication.entrySet()) {
                PrintWriter writer = getWriter(appEntry.getKey());
                if (writer != null) {
                    for (LogEntry logEntry : appEntry.getValue()) {
                        String logLine = formatLogEntry(logEntry);
                        writer.println(logLine);
                        totalLogsStored++;
                        totalBytesWritten += logLine.length();
                    }
                    writer.flush();
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Obtient le writer pour une application
     * Nom de fichier: ApplicationName_YYYY-MM-DD.log
     */
    private PrintWriter getWriter(String applicationName) {
        String fileName = getFileName(applicationName);
        PrintWriter writer = writers.get(fileName);
        
        if (writer == null) {
            lock.readLock().unlock();
            lock.writeLock().lock();
            try {
                writer = writers.get(fileName);
                if (writer == null) {
                    try {
                        Path filePath = Paths.get(baseDirectory, fileName);
                        FileWriter fileWriter = new FileWriter(filePath.toFile(), true);
                        writer = new PrintWriter(fileWriter);
                        writers.put(fileName, writer);
                        
                        System.out.println("Nouveau fichier log: " + fileName);
                    } catch (IOException e) {
                        System.err.println("Erreur création fichier: " + e.getMessage());
                        return null;
                    }
                }
                
                lock.readLock().lock();
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        return writer;
    }
    
    private String getFileName(String applicationName) {
        String date = LocalDate.now().format(dateFormatter);
        return String.format("%s_%s.log", applicationName, date);
    }
    
    /**
     * Formate une entrée pour l'écriture fichier
     * Format: [TIMESTAMP] [LEVEL] [APP] [HOST] MESSAGE {metadata}
     */
    private String formatLogEntry(LogEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(entry.toFormattedString());
        
        // Ajouter métadonnées si présentes
        if (!entry.getMetadata().isEmpty()) {
            sb.append(" {");
            boolean first = true;
            for (Map.Entry<String, String> meta : entry.getMetadata().entrySet()) {
                if (!first) sb.append(", ");
                sb.append(meta.getKey()).append("=").append(meta.getValue());
                first = false;
            }
            sb.append("}");
        }
        
        return sb.toString();
    }
    
    @Override
    public List<LogEntry> getLogsByApplication(String applicationName, int limit) {
        // Implémentation basique - pour lecture des logs stockés
        List<LogEntry> logs = new ArrayList<>();
        String fileName = getFileName(applicationName);
        Path filePath = Paths.get(baseDirectory, fileName);
        
        if (!Files.exists(filePath)) {
            return logs;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null && logs.size() < limit) {
                LogEntry entry = parseLogLine(line);
                if (entry != null) {
                    logs.add(entry);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lecture fichier: " + e.getMessage());
        }
        
        return logs;
    }
    
    @Override
    public List<LogEntry> getLogsByLevel(LogLevel level, int limit) {
        // Parcours de tous les fichiers pour trouver les logs de ce niveau
        List<LogEntry> logs = new ArrayList<>();
        
        try {
            Files.walk(Paths.get(baseDirectory))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".log"))
                .forEach(path -> {
                    try (BufferedReader reader = Files.newBufferedReader(path)) {
                        String line;
                        while ((line = reader.readLine()) != null && logs.size() < limit) {
                            LogEntry entry = parseLogLine(line);
                            if (entry != null && entry.getLevel() == level) {
                                logs.add(entry);
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("Erreur lecture: " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Erreur parcours fichiers: " + e.getMessage());
        }
        
        return logs;
    }
    
    /**
     * Parse une ligne de log (implémentation basique)
     */
    private LogEntry parseLogLine(String line) {
        try {
            // Parsing basique - peut être amélioré avec regex
            if (line.startsWith("[") && line.contains("] ")) {
                String[] parts = line.split("] ", 3);
                if (parts.length >= 3) {
                    String levelPart = parts[1];
                    String rest = parts[2];
                    
                    LogLevel level = LogLevel.fromString(levelPart);
                    
                    if (rest.contains(" - ")) {
                        String[] restParts = rest.split(" - ", 2);
                        String appHost = restParts[0];
                        String message = restParts[1];
                        
                        if (appHost.startsWith("[") && appHost.contains("] ")) {
                            String[] appHostParts = appHost.substring(1).split("] ", 2);
                            String app = appHostParts[0];
                            
                            return new LogEntry(level, message, app);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur parsing: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            for (PrintWriter writer : writers.values()) {
                if (writer != null) {
                    writer.close();
                }
            }
            writers.clear();
            System.out.println("Stockage fermé - " + getStorageStats());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public String getStorageStats() {
        return String.format(
            "Storage Stats - Files: %d, Logs: %d, Bytes: %d MB",
            writers.size(), totalLogsStored, totalBytesWritten / (1024 * 1024)
        );
    }
}