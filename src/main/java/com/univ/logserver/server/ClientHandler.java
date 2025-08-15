package com.univ.logserver.server;

import com.univ.logserver.buffer.CircularBuffer;
import com.univ.logserver.model.LogEntry;
import com.univ.logserver.processor.LogParser;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gestionnaire pour chaque client connecté
 * Traite les connexions client de manière asynchrone
 */
public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final CircularBuffer buffer;
    private final String clientId;
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesRejected = new AtomicLong(0);
    private volatile boolean running = true;
    private final long connectTime = System.currentTimeMillis();

    public ClientHandler(Socket clientSocket, CircularBuffer buffer) {
        this.clientSocket = clientSocket;
        this.buffer = buffer;
        this.clientId = generateClientId();

        // Configuration socket
        try {
            clientSocket.setSoTimeout(30000); // 30 secondes timeout
            clientSocket.setKeepAlive(true);
        } catch (Exception e) {
            System.err.println("Erreur config socket: " + e.getMessage());
        }
    }

    private String generateClientId() {
        return String.format("%s:%d-%d",
                clientSocket.getInetAddress().getHostAddress(),
                clientSocket.getPort(),
                System.currentTimeMillis());
    }

    @Override
    public void run() {
        System.out.println("Client connecté: " + clientId);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(
                        clientSocket.getOutputStream(), true)) {

            // Message de bienvenue
            writer.println("OK:CONNECTED:" + clientId);

            String line;
            while (running && (line = reader.readLine()) != null) {
                try {
                    processMessage(line, writer);
                } catch (Exception e) {
                    System.err.println("Erreur traitement message " + clientId + ": " + e.getMessage());
                    writer.println("ERROR:PROCESSING_FAILED:" + e.getMessage());
                }
            }

        } catch (SocketTimeoutException e) {
            System.out.println("Timeout client: " + clientId);
        } catch (IOException e) {
            if (running) {
                System.err.println("Erreur connexion " + clientId + ": " + e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Traite un message reçu du client
     */
    private void processMessage(String message, PrintWriter writer) {
        if (message == null || message.trim().isEmpty()) {
            writer.println("ERROR:EMPTY_MESSAGE");
            return;
        }

        messagesReceived.incrementAndGet();

        // Commandes spéciales
        if (message.startsWith("CMD:")) {
            handleCommand(message.substring(4), writer);
            return;
        }

        // Validation du message
        if (!LogParser.isValidLogMessage(message)) {
            messagesRejected.incrementAndGet();
            writer.println("ERROR:INVALID_MESSAGE_FORMAT");
            return;
        }

        // Parser le message en LogEntry
        LogEntry logEntry = LogParser.parseLogMessage(message);
        if (logEntry == null) {
            messagesRejected.incrementAndGet();
            writer.println("ERROR:PARSE_FAILED");
            return;
        }

        // Enrichir avec infos client
        LogParser.enrichLogEntry(logEntry, clientSocket.getInetAddress().getHostAddress());
        logEntry.addMetadata("client_id", clientId);

        // Ajouter au buffer
        boolean added = buffer.add(logEntry);
        if (added) {
            writer.println("OK:QUEUED:" + logEntry.getId());

            // Stats périodiques
            if (messagesReceived.get() % 1000 == 0) {
                System.out.println(String.format(
                        "Client %s - Messages: %d, Rejetés: %d",
                        clientId, messagesReceived.get(), messagesRejected.get()));
            }
        } else {
            messagesRejected.incrementAndGet();
            writer.println("ERROR:BUFFER_FULL:BACKPRESSURE_ACTIVE");
        }
    }

    /**
     * Traite les commandes spéciales du client
     */
    private void handleCommand(String command, PrintWriter writer) {
        String[] parts = command.split(":", 2);
        String cmd = parts[0].toUpperCase();

        switch (cmd) {
            case "PING":
                writer.println("OK:PONG");
                break;

            case "STATS":
                writer.println("OK:STATS:" + getClientStats());
                break;

            case "BUFFER_STATS":
                writer.println("OK:BUFFER_STATS:" + buffer.getStats());
                break;

            case "DISCONNECT":
                writer.println("OK:DISCONNECTING");
                running = false;
                break;

            case "HELP":
                writer.println("OK:COMMANDS:PING,STATS,BUFFER_STATS,DISCONNECT,HELP");
                break;

            default:
                writer.println("ERROR:UNKNOWN_COMMAND:" + cmd);
        }
    }

    /**
     * Statistiques du client
     */
    private String getClientStats() {
        long uptime = System.currentTimeMillis() - connectTime;
        double rate = uptime > 0 ? (double) messagesReceived.get() / (uptime / 1000.0) : 0;

        return String.format(
                "Messages:%d,Rejected:%d,Rate:%.2f/s,Uptime:%ds",
                messagesReceived.get(), messagesRejected.get(), rate, uptime / 1000);
    }

    private void cleanup() {
        running = false;
        try {
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erreur fermeture socket: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - connectTime;
        System.out.println(String.format(
                "Client déconnecté: %s - Durée: %ds, Messages: %d, Rejetés: %d",
                clientId, duration / 1000, messagesReceived.get(), messagesRejected.get()));
    }

    public void stop() {
        running = false;
    }

    public String getClientId() {
        return clientId;
    }

    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    public long getMessagesRejected() {
        return messagesRejected.get();
    }

    public long getUptime() {
        return System.currentTimeMillis() - connectTime;
    }
}