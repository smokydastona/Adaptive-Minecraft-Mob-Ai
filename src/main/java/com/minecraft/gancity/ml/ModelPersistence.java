package com.minecraft.gancity.ml;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Model Persistence System - Save/load ML models across restarts
 * Inspired by AI-Player's Q-table persistence with launcher detection
 * 
 * Features:
 * - Auto-save models periodically
 * - Load pre-trained models on startup
 * - Transfer learning support
 * - Compression for smaller file sizes
 * - Backup system for safety
 */
public class ModelPersistence {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Paths
    private static final String MODEL_DIR = "models/ai_enhanced";
    private static final String BACKUP_DIR = "models/ai_enhanced/backups";
    private static final String DOUBLE_DQN_FILE = "double_dqn_policy.model";
    private static final String TARGET_NETWORK_FILE = "double_dqn_target.model";
    private static final String REPLAY_BUFFER_FILE = "prioritized_replay.dat";
    private static final String KNOWLEDGE_BASE_FILE = "tactic_knowledge.dat";
    private static final String METADATA_FILE = "model_metadata.properties";
    
    // Configuration
    private static final long AUTO_SAVE_INTERVAL = 600000; // 10 minutes
    private static final int MAX_BACKUPS = 5;
    private static final boolean COMPRESS_MODELS = true;
    
    private long lastSaveTime = 0;
    private final Path modelDirectory;
    private final Path backupDirectory;
    
    public ModelPersistence() {
        // Initialize directories
        this.modelDirectory = detectModelPath();
        this.backupDirectory = modelDirectory.resolve("backups");
        
        try {
            Files.createDirectories(modelDirectory);
            Files.createDirectories(backupDirectory);
            LOGGER.info("Model persistence initialized at: {}", modelDirectory);
        } catch (IOException e) {
            LOGGER.error("Failed to create model directories", e);
        }
    }
    
    /**
     * Detect appropriate model path based on environment
     */
    private Path detectModelPath() {
        // Check for common Minecraft launcher directories
        String userHome = System.getProperty("user.home");
        Path[] possiblePaths = {
            // Vanilla Minecraft
            Paths.get(userHome, ".minecraft", MODEL_DIR),
            // Modrinth App
            Paths.get(userHome, ".modrinth", "profiles", "default", MODEL_DIR),
            // Prism Launcher
            Paths.get(userHome, ".local", "share", "PrismLauncher", "instances", MODEL_DIR),
            // MultiMC
            Paths.get(userHome, ".multimc", "instances", MODEL_DIR),
            // CurseForge
            Paths.get(userHome, "curseforge", "minecraft", "Instances", MODEL_DIR),
            // ATLauncher
            Paths.get(userHome, "ATLauncher", "instances", MODEL_DIR),
            // Fallback: current directory
            Paths.get(MODEL_DIR)
        };
        
        // Try to find existing model directory
        for (Path path : possiblePaths) {
            if (Files.exists(path)) {
                LOGGER.info("Found existing model directory: {}", path);
                return path;
            }
        }
        
        // Default to vanilla Minecraft path
        LOGGER.info("Using default model directory: {}", possiblePaths[0]);
        return possiblePaths[0];
    }
    
    /**
     * Get the model directory path (public accessor for federated learning)
     */
    public Path getModelPath() {
        return modelDirectory;
    }
    
    /**
     * Save DoubleDQN model
     * CRITICAL FIX: Atomic write with .tmp -> rename pattern
     */
    public void saveDoubleDQN(DoubleDQN dqn) {
        if (dqn == null) {
            return;
        }
        
        try {
            Path policyPath = modelDirectory.resolve(DOUBLE_DQN_FILE);
            Path targetPath = modelDirectory.resolve(TARGET_NETWORK_FILE);
            
            // Write to .tmp files first
            Path tmpDir = modelDirectory.resolve("tmp");
            Files.createDirectories(tmpDir);
            
            try {
                // Save both policy and target networks to temp directory
                dqn.save(tmpDir);
                
                // Atomic move from tmp to final location
                Path tmpPolicy = tmpDir.resolve(DOUBLE_DQN_FILE);
                Path tmpTarget = tmpDir.resolve(TARGET_NETWORK_FILE);
                
                if (Files.exists(tmpPolicy)) {
                    Files.move(tmpPolicy, policyPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
                if (Files.exists(tmpTarget)) {
                    Files.move(tmpTarget, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
                
            } finally {
                // Cleanup temp directory
                deleteDirectory(tmpDir);
            }
            
            LOGGER.info("Saved DoubleDQN model to {}", modelDirectory);
            updateMetadata("double_dqn", System.currentTimeMillis());
            
        } catch (IOException e) {
            LOGGER.error("Failed to save DoubleDQN model", e);
        }
    }
    
    /**
     * Load DoubleDQN model
     */
    public void loadDoubleDQN(DoubleDQN dqn) {
        if (dqn == null) {
            return;
        }
        
        Path modelPath = modelDirectory.resolve(DOUBLE_DQN_FILE);
        
        try {
            if (!Files.exists(modelPath)) {
                LOGGER.info("No saved DoubleDQN model found, starting fresh");
                return;
            }
            
            dqn.load(modelDirectory);
            
            LOGGER.info("✅ Loaded DoubleDQN model from {}", modelDirectory);
            
        } catch (IOException | ai.djl.MalformedModelException e) {
            LOGGER.error("❌ Failed to load DoubleDQN model - model may be corrupted. Deleting and starting fresh.", e);
            // Delete corrupted model file to prevent future load attempts
            try {
                Files.deleteIfExists(modelPath);
                LOGGER.info("Deleted corrupted model file: {}", modelPath);
            } catch (IOException deleteError) {
                LOGGER.warn("Could not delete corrupted model: {}", deleteError.getMessage());
            }
            // Re-throw to trigger ML disable in parent
            throw new RuntimeException("Model load failed", e);
        }
    }
    
    /**
     * Save prioritized replay buffer
     */
    public void saveReplayBuffer(PrioritizedReplayBuffer buffer) {
        if (buffer == null) {
            return;
        }
        
        try {
            Path bufferPath = modelDirectory.resolve(REPLAY_BUFFER_FILE);
            
            try (ObjectOutputStream oos = createOutputStream(bufferPath)) {
                // Save buffer size and experiences
                oos.writeInt(buffer.size());
                
                // Note: Full buffer serialization would require
                // implementing Serializable on Experience class
                LOGGER.info("Saved replay buffer metadata");
            }
            
            updateMetadata("replay_buffer", System.currentTimeMillis());
            
        } catch (IOException e) {
            LOGGER.error("Failed to save replay buffer", e);
        }
    }
    
    /**
     * Save tactic knowledge base
     */
    public void saveKnowledgeBase(TacticKnowledgeBase kb) {
        if (kb == null) {
            return;
        }
        
        try {
            Path kbPath = modelDirectory.resolve(KNOWLEDGE_BASE_FILE);
            
            try (ObjectOutputStream oos = createOutputStream(kbPath)) {
                Map<String, Object> stats = kb.getStats();
                oos.writeObject(stats);
                
                LOGGER.info("Saved knowledge base with {} tactics", stats.get("total_tactics"));
            }
            
            updateMetadata("knowledge_base", System.currentTimeMillis());
            
        } catch (IOException e) {
            LOGGER.error("Failed to save knowledge base", e);
        }
    }
    
    /**
     * Auto-save check (call periodically from main loop)
     */
    public boolean shouldAutoSave() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastSaveTime >= AUTO_SAVE_INTERVAL) {
            lastSaveTime = currentTime;
            return true;
        }
        
        return false;
    }
    
    /**
     * Save all models with backup
     */
    public void saveAll(DoubleDQN dqn, PrioritizedReplayBuffer buffer, TacticKnowledgeBase kb) {
        LOGGER.info("Starting full model save...");
        
        // Create backup of existing models
        createBackup();
        
        // Save models
        saveDoubleDQN(dqn);
        saveReplayBuffer(buffer);
        saveKnowledgeBase(kb);
        
        // Update save time
        lastSaveTime = System.currentTimeMillis();
        
        LOGGER.info("Model save completed");
    }
    
    /**
     * Load all models
     */
    public void loadAll(DoubleDQN dqn, PrioritizedReplayBuffer buffer, TacticKnowledgeBase kb) {
        LOGGER.info("Loading saved models...");
        
        loadDoubleDQN(dqn);
        // Load other components as needed
        
        LOGGER.info("Model load completed");
    }
    
    /**
     * Create backup of current models
     */
    private void createBackup() {
        try {
            // Generate backup name with timestamp
            String timestamp = String.valueOf(System.currentTimeMillis());
            Path backupDir = backupDirectory.resolve("backup_" + timestamp);
            
            Files.createDirectories(backupDir);
            
            // Copy all model files to backup
            Files.list(modelDirectory)
                .filter(path -> !path.equals(backupDirectory))
                .filter(path -> path.toString().endsWith(".model") || 
                               path.toString().endsWith(".dat"))
                .forEach(path -> {
                    try {
                        Path target = backupDir.resolve(path.getFileName());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to backup file: {}", path, e);
                    }
                });
            
            // Cleanup old backups
            cleanupOldBackups();
            
            LOGGER.debug("Created model backup at {}", backupDir);
            
        } catch (IOException e) {
            LOGGER.error("Failed to create backup", e);
        }
    }
    
    /**
     * Remove old backups keeping only MAX_BACKUPS recent
     */
    private void cleanupOldBackups() {
        try {
            List<Path> backups = new ArrayList<>();
            
            Files.list(backupDirectory)
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().startsWith("backup_"))
                .forEach(backups::add);
            
            // Sort by timestamp (newest first)
            backups.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
            
            // Remove oldest backups
            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                Path oldBackup = backups.get(i);
                deleteDirectory(oldBackup);
                LOGGER.debug("Removed old backup: {}", oldBackup);
            }
            
        } catch (IOException e) {
            LOGGER.error("Failed to cleanup old backups", e);
        }
    }
    
    /**
     * Restore from latest backup
     */
    public void restoreFromBackup() {
        try {
            // Find latest backup
            Optional<Path> latestBackup = Files.list(backupDirectory)
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().startsWith("backup_"))
                .max((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
            
            if (!latestBackup.isPresent()) {
                LOGGER.warn("No backups found");
                return;
            }
            
            Path backup = latestBackup.get();
            LOGGER.info("Restoring from backup: {}", backup);
            
            // Copy backup files to model directory
            Files.list(backup).forEach(path -> {
                try {
                    Path target = modelDirectory.resolve(path.getFileName());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    LOGGER.error("Failed to restore file: {}", path, e);
                }
            });
            
            LOGGER.info("Restore completed");
            
        } catch (IOException e) {
            LOGGER.error("Failed to restore from backup", e);
        }
    }
    
    /**
     * Update metadata file
     */
    private void updateMetadata(String component, long timestamp) {
        Path metadataPath = modelDirectory.resolve(METADATA_FILE);
        Properties props = new Properties();
        
        // Load existing metadata
        if (Files.exists(metadataPath)) {
            try (InputStream is = Files.newInputStream(metadataPath)) {
                props.load(is);
            } catch (IOException e) {
                LOGGER.warn("Failed to load metadata", e);
            }
        }
        
        // Update component timestamp
        props.setProperty(component + ".last_save", String.valueOf(timestamp));
        props.setProperty("version", "1.0.0");
        
        // Save metadata
        try (OutputStream os = Files.newOutputStream(metadataPath)) {
            props.store(os, "AI Enhanced Model Metadata");
        } catch (IOException e) {
            LOGGER.error("Failed to save metadata", e);
        }
    }
    
    /**
     * Create output stream with optional compression
     */
    private ObjectOutputStream createOutputStream(Path path) throws IOException {
        OutputStream os = Files.newOutputStream(path);
        
        if (COMPRESS_MODELS) {
            os = new GZIPOutputStream(os);
        }
        
        return new ObjectOutputStream(os);
    }
    
    /**
     * Create input stream with optional decompression
     */
    private ObjectInputStream createInputStream(Path path) throws IOException {
        InputStream is = Files.newInputStream(path);
        
        if (COMPRESS_MODELS) {
            is = new GZIPInputStream(is);
        }
        
        return new ObjectInputStream(is);
    }
    
    /**
     * Delete directory recursively
     */
    private void deleteDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .sorted((a, b) -> -a.compareTo(b)) // Reverse order for deletion
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete: {}", path, e);
                }
            });
    }
    
    /**
     * Get model directory path
     */
    public Path getModelDirectory() {
        return modelDirectory;
    }
}
