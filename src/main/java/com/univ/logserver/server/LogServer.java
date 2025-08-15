package com.univ.logserver.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.univ.logserver.buffer.CircularBuffer;
import com.univ.logserver.config.ServerConfig;
import com.univ.logserver.processor.LogProcessor;
import com.univ.logserver.storage.FileLogStorage;
import com.univ.logserver.storage.LogStorage;

/**
 * Serveur principal de logs centralisé
 * Gère les connexions clients et coordonne le traitement des logs
 */
public class LogServer {
    private final ServerConfig config;
    private final CircularBuffer buffer;
    private final LogStorage storage;
    private final ExecutorService clientExecutor;
    private final ExecutorService processorExecutor;
    private final ScheduledExecutorService statsExecutor;
    
    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger clientCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    
    private LogProcessor[] processors;
    private final long startTime = System.currentTimeMillis();
    
    public LogServer() {
        this.config = ServerConfig.getInstance();
        this.buffer = new CircularBuffer(config.getBufferSize());
        this.storage = new FileLogStorage(config.getStorageType());
        
        // Pool threads pour clients
        this.clientExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "LogServer-Client-" + clientCount.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        
        // Pool threads pour processeurs
        this.processorExecutor = Executors.newFixedThreadPool(
            config.getThreadPoolSize(),
            r -> {
                Thread t = new Thread(r, "LogServer-Processor-" + Math.random());
                t.setDaemon(true);
                return t;
            }
        );
        
        // Thread pour statistiques
        this.statsExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "LogServer-Stats");
            t.setDaemon(true);
            return t;
        });
        
        System.out.println("Serveur initialisé - Port: " + config.getPort());
    }
    
    /**
     * Démarre le serveur
     */
    public void start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("Serveur déjà en cours");
        }
        
        serverSocket = new ServerSocket(config.getPort());
        serverSocket.setSoTimeout(5000); // Timeout pour accept()
        running.set(true);
        
        // Démarrer processeurs et stats
        startProcessors();
        startStatsReporting();
        
        System.out.println("=== SERVEUR DE LOGS CENTRALISÉ DÉMARRÉ ===");
        System.out.println("Port: " + config.getPort());
        System.out.println("Clients max: " + config.getBufferSize());
        System.out.println("Buffer: " + config.getBufferSize());
        System.out.println("Processeurs: " + config.getThreadPoolSize());
        System.out.println("Stockage: " + config.getStorageType());
        System.out.println("==========================================");
        
        // Boucle principale d'acceptation
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                
                // Vérifier limite clients
                if (clients.size() >= config.getBufferSize()) {
                    System.err.println("Limite clients atteinte: " + 
                                     clientSocket.getInetAddress());
                    clientSocket.close();
                    continue;
                }
                
                // Créer gestionnaire client
                ClientHandler clientHandler = new ClientHandler(clientSocket, buffer);
                clients.put(clientHandler.getClientId(), clientHandler);
                
                // Traiter dans thread séparé
                clientExecutor.submit(() -> {
                    try {
                        clientHandler.run();
                    } finally {
                        clients.remove(clientHandler.getClientId());
                    }
                });
                
            } catch (SocketTimeoutException e) {
                // Timeout normal, continuer
                continue;
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Erreur acceptation client: " + e.getMessage());
                }
            }
        }
        
        System.out.println("Serveur arrêté");
    }
    
    private void startProcessors() {
        int processorCount = config.getThreadPoolSize();
        processors = new LogProcessor[processorCount];
        
        int batchSize = Math.max(10, config.getBufferSize() / (processorCount * 10));
        
        for (int i = 0; i < processorCount; i++) {
            processors[i] = new LogProcessor(buffer, storage, batchSize);
            processorExecutor.submit(processors[i]);
            System.out.println("Processeur " + i + " démarré (batch=" + batchSize + ")");
        }
    }
    
    private void startStatsReporting() {
        statsExecutor.scheduleAtFixedRate(() -> {
            try {
                System.out.println("\n=== STATISTIQUES SERVEUR ===");
                System.out.println("Uptime: " + getUptimeString());
                System.out.println("Clients: " + clients.size());
                System.out.println(buffer.getStats());
                System.out.println(storage.getStorageStats());
                
                // Stats processeurs
                long totalProcessed = 0;
                for (LogProcessor processor : processors) {
                    if (processor != null) {
                        totalProcessed += processor.getProcessedLogs();
                    }
                }
                System.out.println("Logs traités: " + totalProcessed);
                
                // Stats clients
                long totalReceived = 0, totalRejected = 0;
                for (ClientHandler client : clients.values()) {
                    totalReceived += client.getMessagesReceived();
                    totalRejected += client.getMessagesRejected();
                }
                System.out.println("Messages: " + totalReceived + ", Rejetés: " + totalRejected);
                System.out.println("==============================\n");
                
            } catch (Exception e) {
                System.err.println("Erreur stats: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Arrête le serveur proprement
     */
    public void stop() {
        if (!running.get()) return;
        
        System.out.println("Arrêt serveur...");
        running.set(false);
        
        try {
            // Fermer socket serveur
            if (serverSocket != null) {
                serverSocket.close();
            }
            
            // Arrêter clients
            System.out.println("Fermeture clients (" + clients.size() + ")...");
            for (ClientHandler client : clients.values()) {
                client.stop();
            }
            
            // Arrêter processeurs
            System.out.println("Arrêt processeurs...");
            for (LogProcessor processor : processors) {
                if (processor != null) {
                    processor.stop();
                }
            }
            
            // Arrêter executors
            shutdownExecutor(clientExecutor, "Client", 10);
            shutdownExecutor(processorExecutor, "Processor", 30);
            shutdownExecutor(statsExecutor, "Stats", 5);
            
            // Fermer stockage
            storage.close();
            
            System.out.println("=== SERVEUR ARRÊTÉ ===");
            System.out.println("Durée: " + getUptimeString());
            System.out.println("======================");
            
        } catch (Exception e) {
            System.err.println("Erreur arrêt: " + e.getMessage());
        }
    }
    
    private void shutdownExecutor(ExecutorService executor, String name, int timeoutSeconds) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                System.out.println("Timeout " + name + " executor, arrêt forcé");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private String getUptimeString() {
        long uptimeMs = System.currentTimeMillis() - startTime;
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        return String.format("%02d:%02d:%02d", hours % 24, minutes % 60, seconds % 60);
    }
    
    /**
     * Hook d'arrêt propre
     */
    public void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nSignal d'arrêt reçu...");
            stop();
        }));
    }
    
    // Getters pour tests et monitoring
    public boolean isRunning() { return running.get(); }
    public int getClientCount() { return clients.size(); }
    public CircularBuffer getBuffer() { return buffer; }
    public LogStorage getStorage() { return storage; }
}