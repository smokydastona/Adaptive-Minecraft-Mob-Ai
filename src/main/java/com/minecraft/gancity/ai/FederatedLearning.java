package com.minecraft.gancity.ai;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Federated Learning System - Syncs learned knowledge to a Git repository
 * allowing all servers to benefit from collective learning while reducing
 * individual server storage pressure.
 * 
 * Features:
 * - Automatic sync to configured Git repository
 * - Privacy-safe aggregation (no player identifiable data)
 * - Exponential backoff for failed syncs
 * - Conflict resolution via merge strategies
 * - Bandwidth optimization with compression
 * 
 * Data Flow:
 * 1. Local server records successes/failures
 * 2. Periodically aggregates to federated format
 * 3. Pulls latest from Git repo
 * 4. Merges local + remote data (weighted average)
 * 5. Pushes aggregated data back
 * 6. All servers download and apply merged knowledge
 */
public class FederatedLearning {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Sync Configuration
    private static final long SYNC_INTERVAL_MS = 300_000; // 5 minutes
    private static final long PULL_INTERVAL_MS = 600_000; // 10 minutes (download more often than push)
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 60_000; // 1 minute base backoff
    
    // File Paths
    private Path localDataPath;
    private Path federatedRepoPath;
    private String repositoryUrl; // Git repository URL
    
    // Sync State
    private long lastSyncTime = 0;
    private long lastPullTime = 0;
    private int consecutiveFailures = 0;
    private boolean syncEnabled = false;
    
    // Thread Pool for Async Operations
    private final ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "FederatedLearning-Sync");
            t.setDaemon(true);
            return t;
        }
    );
    
    // Aggregated Data Structures
    private final Map<String, FederatedTactic> tacticAggregates = new ConcurrentHashMap<>();
    private final Map<String, FederatedBehavior> behaviorAggregates = new ConcurrentHashMap<>();
    
    // Statistics
    private long totalDataPointsContributed = 0;
    private long totalDataPointsDownloaded = 0;
    
    public FederatedLearning(Path localDataPath, String repositoryUrl) {
        this.localDataPath = localDataPath;
        this.repositoryUrl = repositoryUrl;
        this.syncEnabled = repositoryUrl != null && !repositoryUrl.isEmpty();
        
        if (syncEnabled) {
            // Detect Git repository path (same as ModelPersistence)
            this.federatedRepoPath = detectFederatedRepoPath();
            
            // Schedule periodic sync operations
            syncExecutor.scheduleAtFixedRate(this::pullFromRepository, 
                PULL_INTERVAL_MS, PULL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            syncExecutor.scheduleAtFixedRate(this::pushToRepository, 
                SYNC_INTERVAL_MS, SYNC_INTERVAL_MS, TimeUnit.MILLISECONDS);
            
            LOGGER.info("Federated Learning enabled - Repository: {}", repositoryUrl);
            LOGGER.info("Local cache: {}, Remote sync every {} minutes", 
                localDataPath, SYNC_INTERVAL_MS / 60_000);
        } else {
            LOGGER.info("Federated Learning disabled - Set 'federatedLearningRepo' in config to enable");
        }
    }
    
    /**
     * Detect the federated repository path based on launcher type
     */
    private Path detectFederatedRepoPath() {
        try {
            // Use same detection logic as ModelPersistence
            Path minecraftDir = Paths.get(System.getProperty("user.dir"));
            
            // Check for common launcher patterns
            String dirPath = minecraftDir.toString();
            if (dirPath.contains("AppData\\Roaming\\.minecraft")) {
                // Vanilla launcher
                return minecraftDir.resolve("federated_learning");
            } else if (dirPath.contains("ModrinthApp")) {
                return minecraftDir.getParent().resolve("federated_learning");
            } else if (dirPath.contains("PrismLauncher")) {
                return minecraftDir.getParent().resolve("federated_learning");
            } else if (dirPath.contains("MultiMC")) {
                return minecraftDir.getParent().resolve("federated_learning");
            } else if (dirPath.contains("curseforge\\minecraft\\Instances")) {
                return minecraftDir.resolve("federated_learning");
            } else if (dirPath.contains("ATLauncher\\instances")) {
                return minecraftDir.resolve("federated_learning");
            }
            
            // Default fallback
            return minecraftDir.resolve("federated_learning");
        } catch (Exception e) {
            LOGGER.error("Failed to detect federated repo path", e);
            return Paths.get("federated_learning");
        }
    }
    
    /**
     * Record a local success/failure for aggregation
     * Privacy-safe: Only tactic ID and outcome, no player data
     */
    public void recordLocalOutcome(String tacticId, String category, boolean success, Map<String, String> conditions) {
        if (!syncEnabled) return;
        
        FederatedTactic tactic = tacticAggregates.computeIfAbsent(tacticId, id -> 
            new FederatedTactic(id, category, new HashMap<>(conditions)));
        
        tactic.recordOutcome(success);
        totalDataPointsContributed++;
    }
    
    /**
     * Record a behavior success for aggregation
     */
    public void recordBehaviorOutcome(String behaviorId, String mobType, boolean success) {
        if (!syncEnabled) return;
        
        FederatedBehavior behavior = behaviorAggregates.computeIfAbsent(behaviorId, id ->
            new FederatedBehavior(id, mobType));
        
        behavior.recordOutcome(success);
        totalDataPointsContributed++;
    }
    
    /**
     * Pull latest aggregated data from Git repository
     */
    public void pullFromRepository() {
        if (!syncEnabled) return;
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPullTime < PULL_INTERVAL_MS) {
            return; // Too soon
        }
        
        try {
            LOGGER.info("Pulling federated knowledge from repository...");
            
            // Execute git pull
            boolean pullSuccess = executeGitPull();
            
            if (pullSuccess) {
                // Load and merge downloaded data
                mergeRemoteData();
                lastPullTime = currentTime;
                consecutiveFailures = 0;
                LOGGER.info("Successfully pulled federated data - {} tactics, {} behaviors", 
                    tacticAggregates.size(), behaviorAggregates.size());
            } else {
                handleSyncFailure("Git pull failed");
            }
            
        } catch (Exception e) {
            handleSyncFailure("Pull error: " + e.getMessage());
        }
    }
    
    /**
     * Push local aggregated data to Git repository
     */
    public void pushToRepository() {
        if (!syncEnabled) return;
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSyncTime < SYNC_INTERVAL_MS) {
            return; // Too soon
        }
        
        // Check if we have enough data to contribute
        if (totalDataPointsContributed < 10) {
            LOGGER.debug("Not enough local data to contribute ({}), waiting for more...", 
                totalDataPointsContributed);
            return;
        }
        
        try {
            LOGGER.info("Pushing federated knowledge to repository...");
            
            // Save local aggregates to files
            saveLocalAggregates();
            
            // Execute git add, commit, push
            boolean pushSuccess = executeGitPush();
            
            if (pushSuccess) {
                lastSyncTime = currentTime;
                consecutiveFailures = 0;
                LOGGER.info("Successfully pushed {} data points to federated repository", 
                    totalDataPointsContributed);
                totalDataPointsContributed = 0; // Reset counter after successful push
            } else {
                handleSyncFailure("Git push failed");
            }
            
        } catch (Exception e) {
            handleSyncFailure("Push error: " + e.getMessage());
        }
    }
    
    /**
     * Execute git pull command
     */
    private boolean executeGitPull() {
        try {
            // Ensure repository is cloned
            if (!Files.exists(federatedRepoPath.resolve(".git"))) {
                return executeGitClone();
            }
            
            ProcessBuilder pb = new ProcessBuilder("git", "pull", "origin", "main");
            pb.directory(federatedRepoPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return true;
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.warn("Git pull output: {}", line);
                }
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to execute git pull", e);
            return false;
        }
    }
    
    /**
     * Execute git clone command (first time setup)
     */
    private boolean executeGitClone() {
        try {
            LOGGER.info("First time setup - Cloning federated repository...");
            
            Files.createDirectories(federatedRepoPath.getParent());
            
            ProcessBuilder pb = new ProcessBuilder("git", "clone", repositoryUrl, 
                federatedRepoPath.toString());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                LOGGER.info("Successfully cloned federated repository");
                return true;
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.warn("Git clone output: {}", line);
                }
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to execute git clone", e);
            return false;
        }
    }
    
    /**
     * Execute git add, commit, push commands
     */
    private boolean executeGitPush() {
        try {
            // Git add
            ProcessBuilder addPb = new ProcessBuilder("git", "add", ".");
            addPb.directory(federatedRepoPath.toFile());
            Process addProcess = addPb.start();
            addProcess.waitFor();
            
            // Git commit
            String commitMessage = String.format("Federated update: %d data points", 
                totalDataPointsContributed);
            ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "-m", commitMessage);
            commitPb.directory(federatedRepoPath.toFile());
            Process commitProcess = commitPb.start();
            int commitExitCode = commitProcess.waitFor();
            
            // Check if there were changes to commit
            if (commitExitCode != 0) {
                LOGGER.debug("No changes to commit");
                return true; // Not an error, just nothing new
            }
            
            // Git push
            ProcessBuilder pushPb = new ProcessBuilder("git", "push", "origin", "main");
            pushPb.directory(federatedRepoPath.toFile());
            pushPb.redirectErrorStream(true);
            
            Process pushProcess = pushPb.start();
            int pushExitCode = pushProcess.waitFor();
            
            if (pushExitCode == 0) {
                return true;
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(pushProcess.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.warn("Git push output: {}", line);
                }
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to execute git push", e);
            return false;
        }
    }
    
    /**
     * Save local aggregates to files for Git commit
     */
    private void saveLocalAggregates() throws IOException {
        Files.createDirectories(federatedRepoPath);
        
        // Save tactics
        Path tacticsFile = federatedRepoPath.resolve("tactics.dat.gz");
        try (GZIPOutputStream gzOut = new GZIPOutputStream(new FileOutputStream(tacticsFile.toFile()));
             ObjectOutputStream out = new ObjectOutputStream(gzOut)) {
            out.writeObject(new HashMap<>(tacticAggregates));
        }
        
        // Save behaviors
        Path behaviorsFile = federatedRepoPath.resolve("behaviors.dat.gz");
        try (GZIPOutputStream gzOut = new GZIPOutputStream(new FileOutputStream(behaviorsFile.toFile()));
             ObjectOutputStream out = new ObjectOutputStream(gzOut)) {
            out.writeObject(new HashMap<>(behaviorAggregates));
        }
        
        // Save metadata
        Properties metadata = new Properties();
        metadata.setProperty("last_update", String.valueOf(System.currentTimeMillis()));
        metadata.setProperty("data_points", String.valueOf(totalDataPointsContributed));
        metadata.setProperty("tactics_count", String.valueOf(tacticAggregates.size()));
        metadata.setProperty("behaviors_count", String.valueOf(behaviorAggregates.size()));
        
        Path metadataFile = federatedRepoPath.resolve("metadata.properties");
        try (FileOutputStream out = new FileOutputStream(metadataFile.toFile())) {
            metadata.store(out, "Federated Learning Metadata");
        }
    }
    
    /**
     * Load and merge remote data with local aggregates
     */
    @SuppressWarnings("unchecked")
    private void mergeRemoteData() {
        try {
            // Load remote tactics
            Path tacticsFile = federatedRepoPath.resolve("tactics.dat.gz");
            if (Files.exists(tacticsFile)) {
                try (GZIPInputStream gzIn = new GZIPInputStream(new FileInputStream(tacticsFile.toFile()));
                     ObjectInputStream in = new ObjectInputStream(gzIn)) {
                    Map<String, FederatedTactic> remoteTactics = 
                        (Map<String, FederatedTactic>) in.readObject();
                    
                    // Merge with local data (weighted average)
                    for (Map.Entry<String, FederatedTactic> entry : remoteTactics.entrySet()) {
                        FederatedTactic remote = entry.getValue();
                        FederatedTactic local = tacticAggregates.get(entry.getKey());
                        
                        if (local != null) {
                            // Merge: weighted average based on sample count
                            local.mergeWith(remote);
                        } else {
                            // New tactic from remote
                            tacticAggregates.put(entry.getKey(), remote);
                        }
                    }
                    
                    totalDataPointsDownloaded += remoteTactics.size();
                }
            }
            
            // Load remote behaviors
            Path behaviorsFile = federatedRepoPath.resolve("behaviors.dat.gz");
            if (Files.exists(behaviorsFile)) {
                try (GZIPInputStream gzIn = new GZIPInputStream(new FileInputStream(behaviorsFile.toFile()));
                     ObjectInputStream in = new ObjectInputStream(gzIn)) {
                    Map<String, FederatedBehavior> remoteBehaviors = 
                        (Map<String, FederatedBehavior>) in.readObject();
                    
                    for (Map.Entry<String, FederatedBehavior> entry : remoteBehaviors.entrySet()) {
                        FederatedBehavior remote = entry.getValue();
                        FederatedBehavior local = behaviorAggregates.get(entry.getKey());
                        
                        if (local != null) {
                            local.mergeWith(remote);
                        } else {
                            behaviorAggregates.put(entry.getKey(), remote);
                        }
                    }
                    
                    totalDataPointsDownloaded += remoteBehaviors.size();
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to merge remote data", e);
        }
    }
    
    /**
     * Handle sync failures with exponential backoff
     */
    private void handleSyncFailure(String reason) {
        consecutiveFailures++;
        LOGGER.warn("Federated sync failed (attempt {}): {}", consecutiveFailures, reason);
        
        if (consecutiveFailures >= MAX_RETRY_ATTEMPTS) {
            long backoffTime = RETRY_BACKOFF_MS * (long) Math.pow(2, consecutiveFailures - MAX_RETRY_ATTEMPTS);
            LOGGER.warn("Multiple sync failures, backing off for {} seconds", backoffTime / 1000);
            lastSyncTime = System.currentTimeMillis() + backoffTime;
        }
    }
    
    /**
     * Get aggregated tactic success rate from federated data
     */
    public float getFederatedTacticSuccessRate(String tacticId) {
        FederatedTactic tactic = tacticAggregates.get(tacticId);
        return tactic != null ? tactic.getSuccessRate() : 0.5f;
    }
    
    /**
     * Get aggregated behavior success rate from federated data
     */
    public float getFederatedBehaviorSuccessRate(String behaviorId) {
        FederatedBehavior behavior = behaviorAggregates.get(behaviorId);
        return behavior != null ? behavior.getSuccessRate() : 0.5f;
    }
    
    /**
     * Get all federated tactics for a category
     */
    public List<FederatedTactic> getTacticsForCategory(String category) {
        return tacticAggregates.values().stream()
            .filter(t -> t.category.equals(category))
            .toList();
    }
    
    /**
     * Shutdown federated learning system
     */
    public void shutdown() {
        LOGGER.info("Shutting down Federated Learning system...");
        
        if (syncEnabled) {
            // Final sync before shutdown
            pushToRepository();
        }
        
        syncExecutor.shutdown();
        try {
            syncExecutor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Federated Learning shutdown interrupted");
        }
    }
    
    /**
     * Get sync statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", syncEnabled);
        stats.put("repository_url", repositoryUrl);
        stats.put("tactics_count", tacticAggregates.size());
        stats.put("behaviors_count", behaviorAggregates.size());
        stats.put("contributed_data_points", totalDataPointsContributed);
        stats.put("downloaded_data_points", totalDataPointsDownloaded);
        stats.put("last_sync", new Date(lastSyncTime));
        stats.put("last_pull", new Date(lastPullTime));
        stats.put("consecutive_failures", consecutiveFailures);
        return stats;
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * Federated Tactic - Privacy-safe aggregated tactic data
     */
    public static class FederatedTactic implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final String tacticId;
        public final String category;
        public final Map<String, String> conditions;
        
        private int totalAttempts = 0;
        private int successfulAttempts = 0;
        private float successRate = 0.5f;
        
        public FederatedTactic(String tacticId, String category, Map<String, String> conditions) {
            this.tacticId = tacticId;
            this.category = category;
            this.conditions = conditions;
        }
        
        public void recordOutcome(boolean success) {
            totalAttempts++;
            if (success) successfulAttempts++;
            successRate = (float) successfulAttempts / totalAttempts;
        }
        
        public void mergeWith(FederatedTactic other) {
            // Weighted average based on sample size
            int combinedAttempts = this.totalAttempts + other.totalAttempts;
            if (combinedAttempts > 0) {
                float weight1 = (float) this.totalAttempts / combinedAttempts;
                float weight2 = (float) other.totalAttempts / combinedAttempts;
                this.successRate = (this.successRate * weight1) + (other.successRate * weight2);
                this.totalAttempts = combinedAttempts;
                this.successfulAttempts = (int) (this.successRate * combinedAttempts);
            }
        }
        
        public float getSuccessRate() {
            return successRate;
        }
        
        public int getTotalAttempts() {
            return totalAttempts;
        }
    }
    
    /**
     * Federated Behavior - Privacy-safe aggregated behavior data
     */
    public static class FederatedBehavior implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final String behaviorId;
        public final String mobType;
        
        private int totalAttempts = 0;
        private int successfulAttempts = 0;
        private float successRate = 0.5f;
        
        public FederatedBehavior(String behaviorId, String mobType) {
            this.behaviorId = behaviorId;
            this.mobType = mobType;
        }
        
        public void recordOutcome(boolean success) {
            totalAttempts++;
            if (success) successfulAttempts++;
            successRate = (float) successfulAttempts / totalAttempts;
        }
        
        public void mergeWith(FederatedBehavior other) {
            int combinedAttempts = this.totalAttempts + other.totalAttempts;
            if (combinedAttempts > 0) {
                float weight1 = (float) this.totalAttempts / combinedAttempts;
                float weight2 = (float) other.totalAttempts / combinedAttempts;
                this.successRate = (this.successRate * weight1) + (other.successRate * weight2);
                this.totalAttempts = combinedAttempts;
                this.successfulAttempts = (int) (this.successRate * combinedAttempts);
            }
        }
        
        public float getSuccessRate() {
            return successRate;
        }
        
        public int getTotalAttempts() {
            return totalAttempts;
        }
    }
}
