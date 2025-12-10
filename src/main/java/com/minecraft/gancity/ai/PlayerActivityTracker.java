package com.minecraft.gancity.ai;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Sleeping system for inactive/AFK players
 * Based on PlayerSync-Plus optimizations
 * 
 * Features:
 * - Skip AI processing for inactive players
 * - Massive server-side CPU savings
 * - Configurable inactivity threshold
 * - Memory-efficient with FastUtil
 */
public class PlayerActivityTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DEFAULT_INACTIVITY_THRESHOLD_MS = 60 * 1000; // 1 minute
    
    // Track last activity time per player
    private final Object2LongOpenHashMap<UUID> lastActivity = new Object2LongOpenHashMap<>();
    private final long inactivityThresholdMs;
    
    private long skippedProcessingCount = 0;
    
    public PlayerActivityTracker() {
        this(DEFAULT_INACTIVITY_THRESHOLD_MS);
    }
    
    public PlayerActivityTracker(long inactivityThresholdMs) {
        this.inactivityThresholdMs = inactivityThresholdMs;
        lastActivity.defaultReturnValue(-1L);
    }
    
    /**
     * Record player activity
     */
    public void recordActivity(UUID playerId) {
        lastActivity.put(playerId, System.currentTimeMillis());
    }
    
    /**
     * Check if player is active (not sleeping/AFK)
     */
    public boolean isActive(UUID playerId) {
        long lastTime = lastActivity.getLong(playerId);
        if (lastTime == -1L) {
            // First time seeing this player, consider active
            recordActivity(playerId);
            return true;
        }
        
        long timeSinceActivity = System.currentTimeMillis() - lastTime;
        return timeSinceActivity < inactivityThresholdMs;
    }
    
    /**
     * Check if player should be skipped for AI processing
     */
    public boolean shouldSkipPlayer(UUID playerId) {
        boolean skip = !isActive(playerId);
        if (skip) {
            skippedProcessingCount++;
            LOGGER.debug("Skipping inactive player: {}", playerId);
        }
        return skip;
    }
    
    /**
     * Remove player tracking (on logout)
     */
    public void removePlayer(UUID playerId) {
        lastActivity.removeLong(playerId);
    }
    
    /**
     * Get time since last activity
     */
    public long getTimeSinceActivity(UUID playerId) {
        long lastTime = lastActivity.getLong(playerId);
        if (lastTime == -1L) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() - lastTime;
    }
    
    /**
     * Get statistics
     */
    public ActivityStats getStats() {
        return new ActivityStats(
            lastActivity.size(),
            skippedProcessingCount,
            inactivityThresholdMs
        );
    }
    
    /**
     * Clear all tracking data
     */
    public void clear() {
        lastActivity.clear();
        skippedProcessingCount = 0;
    }
    
    /**
     * Activity tracking statistics
     */
    public static class ActivityStats {
        public final int trackedPlayers;
        public final long skippedProcessing;
        public final long inactivityThreshold;
        
        ActivityStats(int trackedPlayers, long skippedProcessing, long inactivityThreshold) {
            this.trackedPlayers = trackedPlayers;
            this.skippedProcessing = skippedProcessing;
            this.inactivityThreshold = inactivityThreshold;
        }
        
        @Override
        public String toString() {
            return String.format("Activity: %d players tracked, %d skipped, threshold: %dms",
                trackedPlayers, skippedProcessing, inactivityThreshold);
        }
    }
}
