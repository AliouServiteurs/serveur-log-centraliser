package com.univ.logserver.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration principale du serveur - Version corrigée
 */
public class ServerConfig {
    private int port = 8080;
    private int bufferSize = 1000;
    private String logFormat = "text";
    private String storageType = "file";
    private int threadPoolSize = 10;
    
    private static ServerConfig instance;
    
    private ServerConfig() {}
    
    public static synchronized ServerConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }
    
    public static ServerConfig load() {
        ServerConfig config = new ServerConfig();
        Properties props = new Properties();
        
        try (InputStream input = ServerConfig.class.getClassLoader()
             .getResourceAsStream("application.properties")) {
            
            if (input != null) {
                props.load(input);
                config.port = Integer.parseInt(props.getProperty("server.port", "8080"));
                config.bufferSize = Integer.parseInt(props.getProperty("buffer.size", "1000"));
                config.logFormat = props.getProperty("log.format", "text");
                config.storageType = props.getProperty("storage.type", "file");
                config.threadPoolSize = Integer.parseInt(props.getProperty("thread.pool.size", "10"));
                
                System.out.println("Configuration chargée depuis application.properties");
            } else {
                System.out.println("Fichier application.properties non trouvé, utilisation des valeurs par défaut");
            }
            
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erreur de chargement de la configuration, utilisation des valeurs par défaut: " + e.getMessage());
        }
        
        return config;
    }
    
    // Getters
    public int getPort() { return port; }
    public int getBufferSize() { return bufferSize; }
    public String getLogFormat() { return logFormat; }
    public String getStorageType() { return storageType; }
    public int getThreadPoolSize() { return threadPoolSize; }
    
    // Setters pour les tests
    public void setPort(int port) { this.port = port; }
    public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }
    public void setLogFormat(String logFormat) { this.logFormat = logFormat; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }
    
    @Override
    public String toString() {
        return String.format("ServerConfig{port=%d, bufferSize=%d, logFormat='%s', storageType='%s', threadPoolSize=%d}",
                           port, bufferSize, logFormat, storageType, threadPoolSize);
    }
}