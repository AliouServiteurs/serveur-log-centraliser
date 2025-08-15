package com.univ.logserver.processor;

import com.univ.logserver.buffer.CircularBuffer;
import com.univ.logserver.model.LogEntry;
import com.univ.logserver.storage.LogStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Processeur de logs multi-threadé
 * Traite les logs du buffer et les stocke de manière asynchrone
 */
public class LogProcessor implements Runnable {
    private final CircularBuffer buffer;
    private final LogStorage storage;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final int batchSize;
    private final long batchTimeoutMs;
    
    // Statistiques
    private final AtomicLong processedLogs = new AtomicLong(0);
    private final AtomicLong batchesProcessed = new AtomicLong(0);
    private volatile long lastProcessTime = System.currentTimeMillis();
    
    public LogProcessor(CircularBuffer buffer, LogStorage storage, int batchSize) {
        this.buffer = buffer;
        this.storage = storage;
        this.batchSize = batchSize;
        this.batchTimeoutMs = 5000; // 5 secondes timeout pour les batch
    }
    
    @Override
    public void run() {
        System.out.println("Processeur démarré - Thread: " + Thread.currentThread().getName());
        
        List<LogEntry> batch = new ArrayList<>();
        long lastBatchTime = System.currentTimeMillis();
        
        while (running.get() || !buffer.isEmpty()) {
            try {
                // Collecter les logs par batch pour optimiser le stockage
                LogEntry entry = buffer.poll(); // Non-bloquant
                
                if (entry != null) {
                    batch.add(entry);
                    lastProcessTime = System.currentTimeMillis();
                }
                
                // Traiter le batch si:
                // 1. Il est plein
                // 2. Il y a un timeout
                // 3. Le processeur s'arrête
                long currentTime = System.currentTimeMillis();
                boolean shouldProcess = batch.size() >= batchSize ||
                                      (currentTime - lastBatchTime > batchTimeoutMs && !batch.isEmpty()) ||
                                      (!running.get() && !batch.isEmpty());
                
                if (shouldProcess) {
                    processBatch(new ArrayList<>(batch));
                    batch.clear();
                    lastBatchTime = currentTime;
                }
                
                // Petite pause si aucun log à traiter
                if (entry == null && running.get()) {
                    Thread.sleep(100);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Processeur interrompu");
                break;
            } catch (Exception e) {
                System.err.println("Erreur processeur: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Processeur arrêté - " + getStats());
    }
    
    /**
     * Traite un batch de logs
     */
    private void processBatch(List<LogEntry> batch) {
        if (batch.isEmpty()) return;
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Pré-traitement des logs (filtres, enrichissement, etc.)
            for (LogEntry entry : batch) {
                preprocessLog(entry);
            }
            
            // Stockage par batch (plus efficace)
            storage.storeBatch(batch);
            
            // Mise à jour statistiques
            processedLogs.addAndGet(batch.size());
            batchesProcessed.incrementAndGet();
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Log des performances périodiquement
            if (batchesProcessed.get() % 100 == 0) {
                System.out.println(String.format(
                    "Batch traité - Logs: %d, Temps: %dms, Thread: %s",
                    batch.size(), processingTime, Thread.currentThread().getName()
                ));
            }
            
        } catch (Exception e) {
            System.err.println("Erreur traitement batch: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Pré-traite une entrée de log
     */
    private void preprocessLog(LogEntry entry) {
        // Ajouter métadonnées de processing
        entry.addMetadata("processor_thread", Thread.currentThread().getName());
        entry.addMetadata("processed_at", String.valueOf(System.currentTimeMillis()));
        
        // Filtrage et validation supplémentaires
        if (entry.getMessage().length() > 5000) {
            entry.addMetadata("truncated", "true");
        }
        
        // Classification automatique
        classifyLog(entry);
    }
    
    /**
     * Classifie automatiquement un log
     */
    private void classifyLog(LogEntry entry) {
        String message = entry.getMessage().toLowerCase();
        
        // Classification par composant
        if (message.contains("sql") || message.contains("database") || message.contains("query")) {
            entry.addMetadata("component", "database");
        } else if (message.contains("http") || message.contains("request") || message.contains("response")) {
            entry.addMetadata("component", "web");
        } else if (message.contains("memory") || message.contains("gc") || message.contains("heap")) {
            entry.addMetadata("component", "memory");
        } else if (message.contains("security") || message.contains("auth") || message.contains("login")) {
            entry.addMetadata("component", "security");
        }
        
        // Classification par sévérité
        if (entry.getLevel().getPriority() >= 5) { // ERROR et FATAL
            entry.addMetadata("severity", "high");
        } else if (entry.getLevel().getPriority() >= 4) { // WARN
            entry.addMetadata("severity", "medium");
        } else {
            entry.addMetadata("severity", "low");
        }
    }
    
    /**
     * Arrête le processeur proprement
     */
    public void stop() {
        running.set(false);
        System.out.println("Arrêt processeur demandé...");
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Statistiques du processeur
     */
    public String getStats() {
        long totalProcessed = processedLogs.get();
        long totalBatches = batchesProcessed.get();
        double avgBatchSize = totalBatches > 0 ? (double) totalProcessed / totalBatches : 0;
        
        return String.format(
            "Processor Stats - Logs: %d, Batches: %d, Avg/Batch: %.1f, Last: %dms ago",
            totalProcessed, totalBatches, avgBatchSize,
            System.currentTimeMillis() - lastProcessTime
        );
    }
    
    public long getProcessedLogs() {
        return processedLogs.get();
    }
    
    public long getBatchesProcessed() {
        return batchesProcessed.get();
    }
}