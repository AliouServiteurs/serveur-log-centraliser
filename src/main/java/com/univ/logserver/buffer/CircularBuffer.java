package com.univ.logserver.buffer;

import com.univ.logserver.model.LogEntry;
import com.univ.logserver.model.LogLevel;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Buffer circulaire thread-safe avec mécanisme de back-pressure
 * Implémente producteur-consommateur avec gestion de surcharge
 */
public class CircularBuffer {
    private final LogEntry[] buffer;
    private final int capacity;
    private volatile int writeIndex = 0;
    private volatile int readIndex = 0;
    private final AtomicInteger size = new AtomicInteger(0);
    
    // Synchronisation
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    
    // Statistiques pour back-pressure
    private final AtomicInteger totalAdded = new AtomicInteger(0);
    private final AtomicInteger totalDropped = new AtomicInteger(0);
    private volatile boolean backPressureActive = false;
    
    public CircularBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new LogEntry[capacity];
    }
    
    /**
     * Ajoute une entrée avec gestion du back-pressure
     * @param entry Entrée à ajouter
     * @return true si ajouté, false si rejeté par back-pressure
     */
    public boolean add(LogEntry entry) {
        lock.lock();
        try {
            totalAdded.incrementAndGet();
            
            // Gestion du back-pressure à 90% de capacité
            if (size.get() >= capacity * 0.9) {
                backPressureActive = true;
                
                if (size.get() >= capacity) {
                    // Buffer plein - supprimer ancien log de faible priorité
                    LogEntry removed = removeOldestLowPriorityEntry();
                    if (removed != null) {
                        totalDropped.incrementAndGet();
                        System.err.println("Back-pressure: Log supprimé - " + removed.getLevel());
                    } else {
                        // Aucun log de faible priorité - rejeter la nouvelle entrée
                        totalDropped.incrementAndGet();
                        System.err.println("Back-pressure: Buffer plein, entrée rejetée");
                        return false;
                    }
                }
            } else if (size.get() < capacity * 0.7) {
                backPressureActive = false;
            }
            
            // Ajouter la nouvelle entrée
            buffer[writeIndex] = entry;
            writeIndex = (writeIndex + 1) % capacity;
            size.incrementAndGet();
            
            notEmpty.signal();
            return true;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Retire une entrée (bloquant si vide)
     */
    public LogEntry take() throws InterruptedException {
        lock.lock();
        try {
            while (size.get() == 0) {
                notEmpty.await();
            }
            
            LogEntry entry = buffer[readIndex];
            buffer[readIndex] = null; // Éviter fuites mémoire
            readIndex = (readIndex + 1) % capacity;
            size.decrementAndGet();
            
            notFull.signal();
            return entry;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Retire une entrée (non-bloquant)
     */
    public LogEntry poll() {
        lock.lock();
        try {
            if (size.get() == 0) {
                return null;
            }
            
            LogEntry entry = buffer[readIndex];
            buffer[readIndex] = null;
            readIndex = (readIndex + 1) % capacity;
            size.decrementAndGet();
            
            notFull.signal();
            return entry;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Supprime la plus ancienne entrée de faible priorité (DEBUG/TRACE)
     */
    private LogEntry removeOldestLowPriorityEntry() {
        int index = readIndex;
        for (int i = 0; i < size.get(); i++) {
            LogEntry entry = buffer[index];
            if (entry != null && 
                (entry.getLevel() == LogLevel.DEBUG || entry.getLevel() == LogLevel.TRACE)) {
                
                LogEntry removed = entry;
                buffer[index] = null;
                shiftElements(index);
                size.decrementAndGet();
                return removed;
            }
            index = (index + 1) % capacity;
        }
        
        // Aucune entrée de faible priorité - supprimer la plus ancienne
        if (size.get() > 0) {
            LogEntry removed = buffer[readIndex];
            buffer[readIndex] = null;
            readIndex = (readIndex + 1) % capacity;
            size.decrementAndGet();
            return removed;
        }
        
        return null;
    }
    
    /**
     * Décale les éléments après suppression
     */
    private void shiftElements(int removedIndex) {
        int current = removedIndex;
        while (current != writeIndex) {
            int next = (current + 1) % capacity;
            if (next == writeIndex) break;
            buffer[current] = buffer[next];
            current = next;
        }
        writeIndex = current;
    }
    
    // Méthodes d'information
    public int size() { return size.get(); }
    public boolean isEmpty() { return size.get() == 0; }
    public boolean isFull() { return size.get() >= capacity; }
    public boolean isBackPressureActive() { return backPressureActive; }
    public int getTotalAdded() { return totalAdded.get(); }
    public int getTotalDropped() { return totalDropped.get(); }
    
    public double getCapacityUsage() {
        return (double) size.get() / capacity * 100.0;
    }
    
    public String getStats() {
        return String.format(
            "Buffer Stats - Size: %d/%d (%.1f%%), Added: %d, Dropped: %d, BackPressure: %s",
            size.get(), capacity, getCapacityUsage(), 
            getTotalAdded(), getTotalDropped(), isBackPressureActive()
        );
    }
}
