package com.univ.logserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.univ.logserver.model.LogEntry;
import com.univ.logserver.model.LogLevel;


@SpringBootTest
class ServerCentraliseApplicationTests {

	@Test
	void contextLoads() {
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

}
