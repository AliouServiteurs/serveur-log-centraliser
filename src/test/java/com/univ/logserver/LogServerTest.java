package com.univ.logserver;

import org.junit.jupiter.api.*;

import com.univ.logserver.buffer.CircularBuffer;
import com.univ.logserver.client.LogClient;
import com.univ.logserver.model.LogEntry;
import com.univ.logserver.model.LogLevel;
import com.univ.logserver.processor.LogParser;
import com.univ.logserver.server.LogServer;
import com.univ.logserver.storage.FileLogStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de tests complète pour le serveur de logs centralisé
 * Couvre tous les composants et cas d'usage critiques
 */
public class LogServerTest {
    
    private static final int TEST_PORT = 8081;
    private static final String TEST_HOST = "localhost";
    private static Path tempDir;
    
    @BeforeAll
    static void setupAll() throws IOException {
        tempDir = Files.createTempDirectory("logserver-test");
        System.out.println("🧪 Répertoire de test: " + tempDir);
    }
    
    @AfterAll
    static void tearDownAll() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Erreur suppression: " + e.getMessage());
                    }
                });
        }
    }
    
    /**
     * Test des modèles de données LogEntry
     */
    @Test
    @DisplayName("Test LogEntry - Création et métadonnées")
    void testLogEntry() {
        LogEntry entry = new LogEntry(LogLevel.INFO, "Test message", "TestApp");
        
        // Vérifications de base
        assertNotNull(entry.getId(), "L'ID ne doit pas être null");
        assertNotNull(entry.getTimestamp(), "Le timestamp ne doit pas être null");
        assertEquals(LogLevel.INFO, entry.getLevel(), "Le niveau doit correspondre");
        assertEquals("Test message", entry.getMessage(), "Le message doit correspondre");
        assertEquals("TestApp", entry.getApplicationName(), "Le nom d'app doit correspondre");
        
        // Test métadonnées
        entry.addMetadata("key1", "value1");
        assertTrue(entry.getMetadata().containsKey("key1"), "Les métadonnées doivent être présentes");
        assertEquals("value1", entry.getMetadata().get("key1"), "La valeur doit correspondre");
        
        // Test sérialisation JSON
        String json = entry.toJson();
        assertNotNull(json, "Le JSON ne doit pas être null");
        assertTrue(json.contains("\"message\":\"Test message\""), "Le JSON doit contenir le message");
        assertTrue(json.contains("\"level\":\"INFO\""), "Le JSON doit contenir le niveau");
        
        // Test format d'affichage
        String formatted = entry.toFormattedString();
        assertNotNull(formatted, "Le format d'affichage ne doit pas être null");
        assertTrue(formatted.contains("INFO"), "Le format doit contenir le niveau");
        assertTrue(formatted.contains("TestApp"), "Le format doit contenir l'application");
    }
    
    /**
     * Test du parser de logs avec différents formats
     */
    @Test
    @DisplayName("Test LogParser - Formats multiples")
    void testLogParser() {
        // Test format complet
        String message = "INFO|MyApp|server01|Application started|user=123,session=abc";
        LogEntry entry = LogParser.parseLogMessage(message);
        
        assertNotNull(entry, "L'entrée parsée ne doit pas être null");
        assertEquals(LogLevel.INFO, entry.getLevel(), "Le niveau doit être INFO");
        assertEquals("Application started", entry.getMessage(), "Le message doit correspondre");
        assertEquals("MyApp", entry.getApplicationName(), "L'application doit correspondre");
        assertEquals("server01", entry.getHostname(), "Le hostname doit correspondre");
        
        // Vérifier métadonnées parsées
        assertTrue(entry.getMetadata().containsKey("user"), "Les métadonnées 'user' doivent être présentes");
        assertEquals("123", entry.getMetadata().get("user"), "La valeur 'user' doit correspondre");
        assertTrue(entry.getMetadata().containsKey("session"), "Les métadonnées 'session' doivent être présentes");
        assertEquals("abc", entry.getMetadata().get("session"), "La valeur 'session' doit correspondre");
        
        // Test format simple
        String simpleMessage = "ERROR Critical failure";
        LogEntry simpleEntry = LogParser.parseLogMessage(simpleMessage);
        assertNotNull(simpleEntry, "L'entrée simple ne doit pas être null");
        assertEquals(LogLevel.ERROR, simpleEntry.getLevel(), "Le niveau doit être ERROR");
        assertEquals("Critical failure", simpleEntry.getMessage(), "Le message doit correspondre");
        
        // Test messages invalides
        assertNotNull(LogParser.parseLogMessage(""), "Un message vide doit retourner une entrée par défaut");
        assertNull(LogParser.parseLogMessage(null), "Un message null doit retourner null");
        
        // Test validation
        assertTrue(LogParser.isValidLogMessage("Valid message"), "Un message valide doit passer");
        assertFalse(LogParser.isValidLogMessage(""), "Un message vide ne doit pas passer");
        assertFalse(LogParser.isValidLogMessage(null), "Un message null ne doit pas passer");
    }
    
    /**
     * Test du buffer circulaire avec back-pressure
     */
    @Test
    @DisplayName("Test CircularBuffer - Fonctionnement et back-pressure")
    void testCircularBuffer() throws InterruptedException {
        CircularBuffer buffer = new CircularBuffer(5);
        
        // État initial
        assertTrue(buffer.isEmpty(), "Le buffer doit être vide initialement");
        assertEquals(0, buffer.size(), "La taille doit être 0");
        assertFalse(buffer.isBackPressureActive(), "Le back-pressure ne doit pas être actif");
        
        // Ajouter des éléments
        LogEntry entry1 = new LogEntry(LogLevel.INFO, "Message 1", "App1");
        LogEntry entry2 = new LogEntry(LogLevel.ERROR, "Message 2", "App2");
        
        assertTrue(buffer.add(entry1), "L'ajout doit réussir");
        assertTrue(buffer.add(entry2), "L'ajout doit réussir");
        assertEquals(2, buffer.size(), "La taille doit être 2");
        assertFalse(buffer.isEmpty(), "Le buffer ne doit plus être vide");
        
        // Récupérer des éléments
        LogEntry retrieved1 = buffer.poll();
        assertNotNull(retrieved1, "L'élément récupéré ne doit pas être null");
        assertEquals("Message 1", retrieved1.getMessage(), "Le premier message doit correspondre");
        assertEquals(1, buffer.size(), "La taille doit être 1 après récupération");
        
        // Test back-pressure - remplir le buffer
        for (int i = 0; i < 10; i++) {
            buffer.add(new LogEntry(LogLevel.DEBUG, "Debug " + i, "TestApp"));
        }
        
        assertTrue(buffer.isBackPressureActive(), "Le back-pressure doit être actif");
        assertTrue(buffer.getTotalDropped() > 0, "Des messages doivent avoir été supprimés");
        
        // Test statistiques
        assertNotNull(buffer.getStats(), "Les stats ne doivent pas être null");
        assertTrue(buffer.getCapacityUsage() >= 0, "L'utilisation doit être positive");
    }
    
    /**
     * Test du stockage sur fichier
     */
    @Test
    @DisplayName("Test FileLogStorage - Stockage et lecture")
    void testFileLogStorage() throws IOException {
        FileLogStorage storage = new FileLogStorage(tempDir.toString());
        
        // Test stockage simple
        LogEntry entry = new LogEntry(LogLevel.INFO, "Test storage", "TestApp");
        entry.addMetadata("test_key", "test_value");
        storage.store(entry);
        
        // Vérifier que le fichier a été créé
        assertTrue(Files.list(tempDir).anyMatch(path -> 
            path.getFileName().toString().contains("TestApp")),
            "Un fichier pour TestApp doit être créé");
        
        // Test stockage par batch
        java.util.List<LogEntry> batch = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            batch.add(new LogEntry(LogLevel.WARN, "Batch message " + i, "BatchApp"));
        }
        storage.storeBatch(batch);
        
        // Vérifier lecture des logs
        java.util.List<LogEntry> logs = storage.getLogsByApplication("TestApp", 10);
        assertFalse(logs.isEmpty(), "Des logs doivent être récupérés");
        
        // Test statistiques
        assertNotNull(storage.getStorageStats(), "Les stats de stockage ne doivent pas être null");
        
        storage.close();
    }
    
    /**
     * Test d'intégration serveur-client complet
     */
    @Test
    @DisplayName("Test Intégration - Serveur et Client")
    void testServerClientIntegration() throws Exception {
        // Configuration pour le test
        System.setProperty("server.port", String.valueOf(TEST_PORT));
        System.setProperty("storage.directory", tempDir.toString());
        
        LogServer server = new LogServer();
        
        // Démarrer le serveur dans un thread
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                fail("Erreur démarrage serveur: " + e.getMessage());
            }
        });
        
        serverThread.start();
        
        // Attendre que le serveur soit prêt
        Thread.sleep(2000);
        
        try {
            // Tester la connexion client
            LogClient client = new LogClient(TEST_HOST, TEST_PORT, "IntegrationTestApp");
            assertTrue(client.connect(), "Le client doit pouvoir se connecter");
            
            // Envoyer quelques logs
            assertTrue(client.sendLog(LogLevel.INFO, "Integration test message 1"),
                      "L'envoi de log INFO doit réussir");
            assertTrue(client.sendLog(LogLevel.ERROR, "Integration test error"),
                      "L'envoi de log ERROR doit réussir");
            assertTrue(client.sendLog(LogLevel.DEBUG, "Integration test debug"),
                      "L'envoi de log DEBUG doit réussir");
            
            assertEquals(3, client.getMessagesSent(), "3 messages doivent avoir été envoyés");
            
            // Tester les commandes
            String pingResponse = client.sendCommand("PING");
            assertTrue(pingResponse.contains("PONG"), "La commande PING doit retourner PONG");
            
            String statsResponse = client.sendCommand("STATS");
            assertTrue(statsResponse.contains("Messages:"), "Les stats doivent contenir 'Messages:'");
            
            String helpResponse = client.sendCommand("HELP");
            assertTrue(helpResponse.contains("COMMANDS"), "L'aide doit contenir 'COMMANDS'");
            
            // Déconnecter le client
            client.disconnect();
            assertFalse(client.isConnected(), "Le client ne doit plus être connecté");
            
        } finally {
            // Arrêter le serveur
            server.stop();
            serverThread.interrupt();
        }
    }
    
    /**
     * Test de charge avec plusieurs clients simultanés
     */
    @Test
    @DisplayName("Test Charge - Clients multiples")
    void testMultipleClientsLoad() throws Exception {
        System.setProperty("server.port", String.valueOf(TEST_PORT + 1));
        System.setProperty("storage.directory", tempDir.toString());
        
        LogServer server = new LogServer();
        
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                fail("Erreur serveur: " + e.getMessage());
            }
        });
        
        serverThread.start();
        Thread.sleep(1000);
        
        try {
            int clientCount = 3;
            int messagesPerClient = 20;
            CountDownLatch latch = new CountDownLatch(clientCount);
            
            // Créer plusieurs clients en parallèle
            for (int i = 0; i < clientCount; i++) {
                final int clientId = i;
                
                Thread clientThread = new Thread(() -> {
                    try {
                        LogClient client = new LogClient(TEST_HOST, TEST_PORT + 1, 
                                                       "LoadTestApp" + clientId);
                        if (client.connect()) {
                            for (int j = 0; j < messagesPerClient; j++) {
                                client.sendLog(LogLevel.INFO, 
                                             String.format("Load test message %d-%d", clientId, j));
                            }
                            assertEquals(messagesPerClient, client.getMessagesSent(),
                                       "Tous les messages doivent être envoyés");
                            client.disconnect();
                        }
                    } catch (Exception e) {
                        fail("Erreur client: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
                
                clientThread.start();
            }
            
            // Attendre que tous les clients terminent
            assertTrue(latch.await(30, TimeUnit.SECONDS), "Tous les clients doivent terminer");
            
            // Vérifier les statistiques
            assertTrue(server.getBuffer().getTotalAdded() >= clientCount * messagesPerClient,
                      "Le buffer doit avoir reçu tous les messages");
            
        } finally {
            server.stop();
            serverThread.interrupt();
        }
    }
    
    /**
     * Test des niveaux de log
     */
    @Test
    @DisplayName("Test LogLevel - Priorités et conversion")
    void testLogLevels() {
        // Vérifier les priorités
        assertEquals(1, LogLevel.TRACE.getPriority(), "TRACE doit avoir priorité 1");
        assertEquals(2, LogLevel.DEBUG.getPriority(), "DEBUG doit avoir priorité 2");
        assertEquals(3, LogLevel.INFO.getPriority(), "INFO doit avoir priorité 3");
        assertEquals(4, LogLevel.WARN.getPriority(), "WARN doit avoir priorité 4");
        assertEquals(5, LogLevel.ERROR.getPriority(), "ERROR doit avoir priorité 5");
        assertEquals(6, LogLevel.FATAL.getPriority(), "FATAL doit avoir priorité 6");
        
        // Test conversion depuis string
        assertEquals(LogLevel.INFO, LogLevel.fromString("info"), "Conversion 'info' doit donner INFO");
        assertEquals(LogLevel.INFO, LogLevel.fromString("INFO"), "Conversion 'INFO' doit donner INFO");
        assertEquals(LogLevel.ERROR, LogLevel.fromString("ERROR"), "Conversion 'ERROR' doit donner ERROR");
        assertEquals(LogLevel.INFO, LogLevel.fromString("unknown"), "Conversion inconnue doit donner INFO (défaut)");
        
        // Test noms
        assertEquals("INFO", LogLevel.INFO.getName(), "Le nom de INFO doit être 'INFO'");
        assertEquals("ERROR", LogLevel.ERROR.getName(), "Le nom de ERROR doit être 'ERROR'");
    }
    
    /**
     * Test de performance du buffer
     */
    @Test
    @DisplayName("Test Performance - Buffer haute charge")
    void testBufferPerformance() {
        CircularBuffer buffer = new CircularBuffer(1000);
        long startTime = System.currentTimeMillis();
        
        // Insérer 5000 éléments
        for (int i = 0; i < 5000; i++) {
            LogEntry entry = new LogEntry(LogLevel.INFO, "Performance test " + i, "PerfApp");
            buffer.add(entry);
        }
        
        long insertTime = System.currentTimeMillis() - startTime;
        
        // Récupérer tous les éléments possibles
        startTime = System.currentTimeMillis();
        int retrieved = 0;
        while (!buffer.isEmpty()) {
            if (buffer.poll() != null) {
                retrieved++;
            }
        }
        
        long retrieveTime = System.currentTimeMillis() - startTime;
        
        System.out.println(String.format("Performance - Insert: %dms, Retrieve: %dms, Elements: %d",
                                        insertTime, retrieveTime, retrieved));
        
        // Vérifications de performance
        assertTrue(insertTime < 5000, "L'insertion doit prendre moins de 5 secondes");
        assertTrue(retrieveTime < 1000, "La récupération doit prendre moins de 1 seconde");
        assertTrue(retrieved <= 1000, "Le nombre récupéré doit être <= capacité du buffer");
    }
    
    /**
     * Test de gestion d'erreurs
     */
    @Test
    @DisplayName("Test Gestion Erreurs - Cas limites")
    void testErrorHandling() {
        // Test buffer avec capacité nulle
        assertThrows(NegativeArraySizeException.class, () -> new CircularBuffer(-1),
                    "Un buffer avec capacité négative doit lever une exception");
        
        // Test parser avec entrées malformées
        LogEntry errorEntry = LogParser.parseLogMessage("|||invalid|||");
        assertNotNull(errorEntry, "Un message malformé doit retourner une entrée d'erreur");
        assertTrue(errorEntry.getMessage().contains("PARSE_ERROR"),
                  "Le message d'erreur doit contenir PARSE_ERROR");
        
        // Test stockage avec répertoire invalide
        assertDoesNotThrow(() -> new FileLogStorage("/invalid/path/that/does/not/exist"),
                          "Le stockage doit gérer gracieusement les répertoires invalides");
    }
    
    /**
     * Test thread-safety
     */
    @Test
    @DisplayName("Test Thread-Safety - Accès concurrent")
    void testThreadSafety() throws InterruptedException {
        CircularBuffer buffer = new CircularBuffer(100);
        int threadCount = 5;
        int messagesPerThread = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // Créer plusieurs threads qui ajoutent des logs
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < messagesPerThread; i++) {
                        LogEntry entry = new LogEntry(LogLevel.INFO, 
                                                     String.format("Thread %d message %d", threadId, i),
                                                     "ThreadTestApp" + threadId);
                        buffer.add(entry);
                        Thread.sleep(1); // Petite pause pour créer de la contention
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
            thread.start();
        }
        
        // Attendre que tous les threads terminent
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Tous les threads doivent terminer");
        
        // Vérifier l'intégrité des données
        assertTrue(buffer.size() > 0, "Le buffer doit contenir des éléments");
        assertTrue(buffer.getTotalAdded() >= threadCount * messagesPerThread,
                  "Le nombre total d'ajouts doit être cohérent");
        
        // Vérifier qu'on peut récupérer les éléments sans corruption
        int retrieved = 0;
        while (!buffer.isEmpty()) {
            LogEntry entry = buffer.poll();
            if (entry != null) {
                assertNotNull(entry.getMessage(), "Le message ne doit pas être null");
                assertNotNull(entry.getApplicationName(), "L'application ne doit pas être null");
                retrieved++;
            }
        }
        
        assertTrue(retrieved > 0, "Des éléments doivent être récupérés");
    }
}