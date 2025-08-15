package com.univ.logserver.client;

import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.univ.logserver.model.LogLevel;

/**
 * Client de test pour envoyer des logs au serveur
 * Simule différentes applications et charges de travail
 */
public class LogClient {
    private final String serverHost;
    private final int serverPort;
    private final String applicationName;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final Random random = new Random();

    public LogClient(String serverHost, int serverPort, String applicationName) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.applicationName = applicationName;
    }

    /**
     * Se connecte au serveur
     */
    public boolean connect() {
        try {
            socket = new Socket(serverHost, serverPort);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Lire la réponse de connexion
            String response = reader.readLine();
            if (response != null && response.startsWith("OK:CONNECTED")) {
                connected.set(true);
                System.out.println("Client connecté: " + response);
                return true;
            } else {
                System.err.println("Erreur connexion: " + response);
                return false;
            }

        } catch (IOException e) {
            System.err.println("Erreur connexion serveur: " + e.getMessage());
            return false;
        }
    }

    /**
     * Envoie un message de log complet
     */
    public boolean sendLog(LogLevel level, String message, String hostname, String metadata) {
        if (!connected.get()) {
            return false;
        }

        try {
            // Format: LEVEL|APPLICATION|HOSTNAME|MESSAGE|metadata
            String logMessage = String.format("%s|%s|%s|%s|%s",
                    level.getName(),
                    applicationName,
                    hostname != null ? hostname : "localhost",
                    message,
                    metadata != null ? metadata : "");

            writer.println(logMessage);

            // Lire la réponse
            String response = reader.readLine();
            if (response != null && response.startsWith("OK")) {
                messagesSent.incrementAndGet();
                return true;
            } else {
                System.err.println("Erreur envoi: " + response);
                return false;
            }

        } catch (IOException e) {
            System.err.println("Erreur envoi log: " + e.getMessage());
            connected.set(false);
            return false;
        }
    }

    /**
     * Envoie un log simple
     */
    public boolean sendLog(LogLevel level, String message) {
        return sendLog(level, message, null, null);
    }

    /**
     * Simule une charge de travail réaliste
     */
    public void simulateLoad(int messageCount, int delayMs) {
        System.out.println("Simulation de charge: " + messageCount + " messages");

        String[] sampleMessages = {
                "Application démarrée avec succès",
                "Connexion base de données établie",
                "Utilisateur connecté: user_%d",
                "Requête SQL exécutée en %dms",
                "Erreur de validation: champ obligatoire manquant",
                "WARNING: Mémoire faible disponible",
                "Cache invalidé pour la clé: session_%d",
                "Transaction terminée avec succès",
                "Erreur réseau: timeout de connexion",
                "Service externe indisponible",
                "Backup programmé démarré",
                "Configuration rechargée",
                "Certificat SSL renouvelé",
                "Processus de nettoyage terminé"
        };

        LogLevel[] levels = {
                LogLevel.INFO, LogLevel.DEBUG, LogLevel.WARN,
                LogLevel.ERROR, LogLevel.TRACE
        };

        String[] components = { "web", "database", "cache", "auth", "scheduler" };

        for (int i = 0; i < messageCount; i++) {
            // Sélectionner message et niveau aléatoires
            String baseMessage = sampleMessages[random.nextInt(sampleMessages.length)];
            LogLevel level = levels[random.nextInt(levels.length)];
            String component = components[random.nextInt(components.length)];

            // Formater le message avec données variables
            String message = String.format(baseMessage,
                    random.nextInt(1000),
                    random.nextInt(500) + 10);

            // Créer métadonnées réalistes
            String metadata = String.format("request_id=%d,user_id=%d,session=%s,component=%s",
                    random.nextInt(10000),
                    random.nextInt(500),
                    "sess_" + random.nextInt(100),
                    component);

            // Envoyer le log
            sendLog(level, message + " [" + i + "]", "test-host-" + (i % 3), metadata);

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (i % 100 == 0) {
                System.out.println("Envoyé: " + i + "/" + messageCount);
            }
        }

        System.out.println("Simulation terminée - Messages envoyés: " + messagesSent.get());
    }

    /**
     * Envoie une commande au serveur
     */
    public String sendCommand(String command) {
        if (!connected.get()) {
            return "ERROR: Not connected";
        }

        try {
            writer.println("CMD:" + command);
            return reader.readLine();
        } catch (IOException e) {
            System.err.println("Erreur commande: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Test de performance avec burst de logs
     */
    public void performanceTest(int burstCount, int burstSize) {
        System.out.println("Test de performance: " + burstCount + " bursts de " + burstSize + " messages");

        long totalStart = System.currentTimeMillis();
        long totalSent = 0;

        for (int burst = 0; burst < burstCount; burst++) {
            long burstStart = System.currentTimeMillis();

            for (int i = 0; i < burstSize; i++) {
                boolean sent = sendLog(LogLevel.INFO,
                        "Performance test message " + (burst * burstSize + i),
                        "perf-host",
                        "burst=" + burst + ",index=" + i);
                if (sent)
                    totalSent++;
            }

            long burstTime = System.currentTimeMillis() - burstStart;
            double burstRate = burstSize / (burstTime / 1000.0);

            System.out.println(String.format("Burst %d: %d messages en %dms (%.1f msg/s)",
                    burst + 1, burstSize, burstTime, burstRate));

            // Petite pause entre les bursts
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        long totalTime = System.currentTimeMillis() - totalStart;
        double totalRate = totalSent / (totalTime / 1000.0);

        System.out.println(String.format("Performance totale: %d messages en %dms (%.1f msg/s)",
                totalSent, totalTime, totalRate));
    }

    /**
     * Ferme la connexion proprement
     */
    public void disconnect() {
        try {
            if (connected.get()) {
                sendCommand("DISCONNECT");
                connected.set(false);
            }

            if (reader != null)
                reader.close();
            if (writer != null)
                writer.close();
            if (socket != null)
                socket.close();

            System.out.println("Client déconnecté - Messages envoyés: " + messagesSent.get());

        } catch (IOException e) {
            System.err.println("Erreur déconnexion: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public long getMessagesSent() {
        return messagesSent.get();
    }

    /**
     * Programme principal pour tests
     */
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        String appName = args.length > 2 ? args[2] : "TestApp";

        LogClient client = new LogClient(host, port, appName);

        if (client.connect()) {
            // Test des commandes
            System.out.println("PING: " + client.sendCommand("PING"));
            System.out.println("HELP: " + client.sendCommand("HELP"));

            // Test de logs variés
            client.sendLog(LogLevel.INFO, "Application de test démarrée");
            client.sendLog(LogLevel.WARN, "Ceci est un avertissement de test");
            client.sendLog(LogLevel.ERROR, "Erreur de test simulée");

            // Simulation de charge
            client.simulateLoad(50, 100);

            // Test de performance
            client.performanceTest(3, 20);

            // Stats finales
            System.out.println("STATS: " + client.sendCommand("STATS"));
            System.out.println("BUFFER_STATS: " + client.sendCommand("BUFFER_STATS"));

            client.disconnect();
        } else {
            System.err.println("Impossible de se connecter au serveur " + host + ":" + port);
        }
    }
}