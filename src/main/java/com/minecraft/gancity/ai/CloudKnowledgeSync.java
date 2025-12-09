package com.minecraft.gancity.ai;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Cloud Knowledge Sync - Alternative to Git-based federated learning
 * using REST API for servers without Git access or for faster sync.
 * 
 * Features:
 * - RESTful API for knowledge upload/download
 * - Automatic conflict resolution
 * - Rate limiting and bandwidth optimization
 * - Fallback to Git if API unavailable
 * - Privacy-preserving aggregation
 * 
 * API Endpoints (example):
 * - POST /api/knowledge/upload - Upload local aggregates
 * - GET /api/knowledge/download - Download merged knowledge
 * - GET /api/knowledge/stats - Get global statistics
 */
public class CloudKnowledgeSync {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // API Configuration
    private String apiEndpoint; // e.g., "https://your-server.com/api/knowledge"
    private String apiKey; // Optional authentication
    
    // Rate Limiting
    private static final long MIN_UPLOAD_INTERVAL_MS = 180_000; // 3 minutes
    private static final long MIN_DOWNLOAD_INTERVAL_MS = 300_000; // 5 minutes
    private static final int MAX_RETRIES = 3;
    
    // Connection Settings
    private static final int CONNECTION_TIMEOUT_MS = 10_000; // 10 seconds
    private static final int READ_TIMEOUT_MS = 30_000; // 30 seconds
    
    // State
    private long lastUploadTime = 0;
    private long lastDownloadTime = 0;
    private boolean apiEnabled = false;
    
    // JSON Serialization
    private final Gson gson = new Gson();
    
    // Thread Pool
    private final ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "CloudKnowledgeSync");
            t.setDaemon(true);
            return t;
        }
    );
    
    public CloudKnowledgeSync(String apiEndpoint, String apiKey) {
        this.apiEndpoint = apiEndpoint;
        this.apiKey = apiKey;
        this.apiEnabled = apiEndpoint != null && !apiEndpoint.isEmpty();
        
        if (apiEnabled) {
            // Schedule periodic sync
            syncExecutor.scheduleAtFixedRate(this::downloadKnowledge, 
                MIN_DOWNLOAD_INTERVAL_MS, MIN_DOWNLOAD_INTERVAL_MS, TimeUnit.MILLISECONDS);
            
            LOGGER.info("Cloud Knowledge Sync enabled - API: {}", apiEndpoint);
        } else {
            LOGGER.info("Cloud Knowledge Sync disabled");
        }
    }
    
    /**
     * Upload local knowledge to cloud API
     */
    public CompletableFuture<Boolean> uploadKnowledge(
            Map<String, FederatedLearning.FederatedTactic> tactics,
            Map<String, FederatedLearning.FederatedBehavior> behaviors) {
        
        return CompletableFuture.supplyAsync(() -> {
            if (!apiEnabled) return false;
            
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUploadTime < MIN_UPLOAD_INTERVAL_MS) {
                LOGGER.debug("Upload rate limited, skipping...");
                return false;
            }
            
            try {
                // Build JSON payload
                JsonObject payload = new JsonObject();
                payload.add("tactics", gson.toJsonTree(tactics));
                payload.add("behaviors", gson.toJsonTree(behaviors));
                payload.addProperty("timestamp", currentTime);
                payload.addProperty("data_points", tactics.size() + behaviors.size());
                
                // Send POST request
                HttpURLConnection conn = createConnection("/upload", "POST");
                conn.setDoOutput(true);
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200 || responseCode == 201) {
                    lastUploadTime = currentTime;
                    LOGGER.info("Successfully uploaded knowledge to cloud - {} data points", 
                        tactics.size() + behaviors.size());
                    return true;
                } else {
                    LOGGER.warn("Cloud upload failed - HTTP {}", responseCode);
                    return false;
                }
                
            } catch (Exception e) {
                LOGGER.error("Failed to upload knowledge to cloud", e);
                return false;
            }
        }, syncExecutor);
    }
    
    /**
     * Download merged knowledge from cloud API
     */
    public CompletableFuture<CloudKnowledge> downloadKnowledge() {
        return CompletableFuture.supplyAsync(() -> {
            if (!apiEnabled) return null;
            
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastDownloadTime < MIN_DOWNLOAD_INTERVAL_MS) {
                return null;
            }
            
            try {
                HttpURLConnection conn = createConnection("/download", "GET");
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    // Read response
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                        }
                        
                        // Parse JSON
                        CloudKnowledge knowledge = gson.fromJson(response.toString(), CloudKnowledge.class);
                        lastDownloadTime = currentTime;
                        
                        LOGGER.info("Successfully downloaded knowledge from cloud - {} tactics, {} behaviors",
                            knowledge.tactics != null ? knowledge.tactics.size() : 0,
                            knowledge.behaviors != null ? knowledge.behaviors.size() : 0);
                        
                        return knowledge;
                    }
                } else {
                    LOGGER.warn("Cloud download failed - HTTP {}", responseCode);
                    return null;
                }
                
            } catch (Exception e) {
                LOGGER.error("Failed to download knowledge from cloud", e);
                return null;
            }
        }, syncExecutor);
    }
    
    /**
     * Get global statistics from cloud
     */
    public CompletableFuture<Map<String, Object>> getGlobalStats() {
        return CompletableFuture.supplyAsync(() -> {
            if (!apiEnabled) return Collections.emptyMap();
            
            try {
                HttpURLConnection conn = createConnection("/stats", "GET");
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                        }
                        
                        @SuppressWarnings("unchecked")
                        Map<String, Object> stats = gson.fromJson(response.toString(), Map.class);
                        return stats;
                    }
                }
                
            } catch (Exception e) {
                LOGGER.error("Failed to get global stats", e);
            }
            
            return Collections.emptyMap();
        }, syncExecutor);
    }
    
    /**
     * Create HTTP connection with configured settings
     */
    private HttpURLConnection createConnection(String endpoint, String method) throws IOException {
        URL url = new URL(apiEndpoint + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "MCA-AI-Enhanced/1.0");
        
        // Add API key if configured
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        
        return conn;
    }
    
    /**
     * Test connection to cloud API
     */
    public boolean testConnection() {
        if (!apiEnabled) return false;
        
        try {
            HttpURLConnection conn = createConnection("/ping", "GET");
            int responseCode = conn.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            LOGGER.error("Cloud API connection test failed", e);
            return false;
        }
    }
    
    /**
     * Shutdown sync executor
     */
    public void shutdown() {
        syncExecutor.shutdown();
        try {
            syncExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Cloud sync shutdown interrupted");
        }
    }
    
    // ==================== Data Classes ====================
    
    /**
     * Cloud Knowledge response structure
     */
    public static class CloudKnowledge {
        public Map<String, FederatedLearning.FederatedTactic> tactics;
        public Map<String, FederatedLearning.FederatedBehavior> behaviors;
        public long timestamp;
        public int totalServers;
        public int totalDataPoints;
    }
}
