package com.univ.logserver;

import java.io.IOException;
import java.util.Scanner;

import com.univ.logserver.server.LogServer;

/**
 * Application principale du serveur de logs centralisé
 * Interface console interactive pour la gestion du serveur
 */
public class LogServerApplication {
    private static LogServer server;
    private static volatile boolean running = true;
    
    public static void main(String[] args) {
        printBanner();
        
        try {
            // Créer et configurer le serveur
            server = new LogServer();
            server.addShutdownHook();
            
            // Démarrer le serveur dans un thread séparé
            Thread serverThread = new Thread(() -> {
                try {
                    server.start();
                } catch (IOException e) {
                    System.err.println("ERREUR FATALE - Démarrage serveur: " + e.getMessage());
                    e.printStackTrace();
                    System.exit(1);
                }
            }, "LogServer-Main");
            
            serverThread.setDaemon(false);
            serverThread.start();
            
            // Attendre que le serveur soit prêt
            Thread.sleep(2000);
            
            // Interface console interactive
            runConsoleInterface();
            
        } catch (Exception e) {
            System.err.println("ERREUR FATALE: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║     SERVEUR DE LOGS CENTRALISÉ       ║");
        System.out.println("║            Version 1.0.0             ║");
        System.out.println("║        Projet de fin de module       ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();
    }
    
    /**
     * Interface console interactive
     */
    private static void runConsoleInterface() {
        Scanner scanner = new Scanner(System.in);
        
        printHelp();
        
        while (running && scanner.hasNextLine()) {
            System.out.print("LogServer> ");
            String input = scanner.nextLine().trim().toLowerCase();
            
            if (input.isEmpty()) {
                continue;
            }
            
            try {
                handleCommand(input);
            } catch (Exception e) {
                System.err.println("Erreur commande: " + e.getMessage());
            }
        }
        
        scanner.close();
    }
    
    /**
     * Traite les commandes console
     */
    private static void handleCommand(String command) {
        switch (command) {
            case "status":
                showStatus();
                break;
                
            case "stats":
                showDetailedStats();
                break;
                
            case "buffer":
                showBufferStats();
                break;
                
            case "storage":
                showStorageStats();
                break;
                
            case "clients":
                showClientStats();
                break;
                
            case "memory":
                showMemoryStats();
                break;
                
            case "gc":
                forceGarbageCollection();
                break;
                
            case "help":
                printHelp();
                break;
                
            case "stop":
                stopServer();
                break;
                
            case "restart":
                restartServer();
                break;
                
            case "quit":
            case "exit":
                quit();
                break;
                
            default:
                System.out.println("❌ Commande inconnue: " + command);
                System.out.println("   Tapez 'help' pour voir les commandes disponibles");
        }
    }
    
    private static void showStatus() {
        System.out.println("\n📊 === STATUT SERVEUR ===");
        if (server == null) {
            System.out.println("❌ Serveur non initialisé");
            return;
        }
        
        System.out.println("🟢 État: " + (server.isRunning() ? "EN COURS" : "ARRÊTÉ"));
        System.out.println("👥 Clients connectés: " + server.getClientCount());
        System.out.println("💾 Mémoire JVM: " + getMemoryInfo());
        System.out.println("🧵 Threads actifs: " + Thread.activeCount());
        System.out.println("========================\n");
    }
    
    private static void showDetailedStats() {
        System.out.println("\n📈 === STATISTIQUES DÉTAILLÉES ===");
        if (server == null) {
            System.out.println("❌ Serveur non initialisé");
            return;
        }
        
        System.out.println("🔄 " + server.getBuffer().getStats());
        System.out.println("💿 " + server.getStorage().getStorageStats());
        System.out.println("🖥️  " + getSystemInfo());
        System.out.println("==================================\n");
    }
    
    private static void showBufferStats() {
        System.out.println("\n🔄 === BUFFER STATS ===");
        if (server == null) {
            System.out.println("❌ Serveur non initialisé");
            return;
        }
        
        System.out.println(server.getBuffer().getStats());
        System.out.println("⚡ Back-pressure: " + 
                          (server.getBuffer().isBackPressureActive() ? "🔴 ACTIF" : "🟢 INACTIF"));
        System.out.println("📊 Utilisation: " + 
                          String.format("%.1f%%", server.getBuffer().getCapacityUsage()));
        System.out.println("======================\n");
    }
    
    private static void showStorageStats() {
        System.out.println("\n💿 === STORAGE STATS ===");
        if (server == null) {
            System.out.println("❌ Serveur non initialisé");
            return;
        }
        
        System.out.println(server.getStorage().getStorageStats());
        System.out.println("=======================\n");
    }
    
    private static void showClientStats() {
        System.out.println("\n👥 === CLIENTS STATS ===");
        if (server == null) {
            System.out.println("❌ Serveur non initialisé");
            return;
        }
        
        System.out.println("Nombre de clients connectés: " + server.getClientCount());
        System.out.println("=======================\n");
    }
    
    private static void showMemoryStats() {
        System.out.println("\n💾 === MÉMOIRE JVM ===");
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        
        System.out.println("🔸 Mémoire utilisée: " + usedMemory + " MB");
        System.out.println("🔸 Mémoire allouée: " + totalMemory + " MB");
        System.out.println("🔸 Mémoire libre: " + freeMemory + " MB");
        System.out.println("🔸 Mémoire maximum: " + maxMemory + " MB");
        System.out.println("🔸 Utilisation: " + String.format("%.1f%%", 
                          (double) usedMemory / maxMemory * 100));
        System.out.println("====================\n");
    }
    
    private static void forceGarbageCollection() {
        System.out.println("🧹 Nettoyage mémoire...");
        long beforeGC = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        System.gc();
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long afterGC = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long freed = (beforeGC - afterGC) / (1024 * 1024);
        
        System.out.println("✅ Nettoyage terminé");
        System.out.println("📉 Mémoire libérée: " + freed + " MB\n");
    }
    
    private static void printHelp() {
        System.out.println("\n🆘 === COMMANDES DISPONIBLES ===");
        System.out.println("📊 status   - Statut général du serveur");
        System.out.println("📈 stats    - Statistiques détaillées complètes");
        System.out.println("🔄 buffer   - État du buffer circulaire");
        System.out.println("💿 storage  - Statistiques de stockage");
        System.out.println("👥 clients  - Informations sur les clients");
        System.out.println("💾 memory   - Statistiques mémoire détaillées");
        System.out.println("🧹 gc       - Force le nettoyage mémoire");
        System.out.println("🔄 restart  - Redémarre le serveur");
        System.out.println("⏹️  stop     - Arrête le serveur");
        System.out.println("🚪 quit     - Ferme l'application");
        System.out.println("🆘 help     - Affiche cette aide");
        System.out.println("===============================\n");
    }
    
    private static void stopServer() {
        if (server != null && server.isRunning()) {
            System.out.println("⏹️  Arrêt du serveur...");
            server.stop();
            System.out.println("✅ Serveur arrêté");
        } else {
            System.out.println("⚠️  Le serveur n'est pas en cours d'exécution");
        }
    }
    
    private static void restartServer() {
        System.out.println("🔄 Redémarrage du serveur...");
        
        if (server != null && server.isRunning()) {
            server.stop();
            try {
                Thread.sleep(2000); // Attendre l'arrêt complet
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Créer nouveau serveur
        server = new LogServer();
        server.addShutdownHook();
        
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                System.err.println("❌ Erreur redémarrage: " + e.getMessage());
            }
        }, "LogServer-Restart");
        
        serverThread.setDaemon(false);
        serverThread.start();
        
        System.out.println("✅ Serveur redémarré");
    }
    
    private static void quit() {
        System.out.println("🚪 Fermeture de l'application...");
        running = false;
        
        if (server != null) {
            server.stop();
        }
        
        System.out.println("👋 Au revoir !");
        System.exit(0);
    }
    
    private static String getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        
        return String.format("%d/%d MB (max: %d MB)", usedMemory, totalMemory, maxMemory);
    }
    
    private static String getSystemInfo() {
        return String.format("OS: %s %s, Java: %s, CPU: %d cores",
                           System.getProperty("os.name"),
                           System.getProperty("os.version"),
                           System.getProperty("java.version"),
                           Runtime.getRuntime().availableProcessors());
    }
}
