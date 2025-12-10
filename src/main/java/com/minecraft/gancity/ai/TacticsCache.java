package com.minecraft.gancity.ai;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Smart caching system for GitHub downloads
 * Based on PlayerSync-Plus optimizations
 * 
 * Features:
 * - TTL (Time To Live) caching
 * - Automatic cache invalidation
 * - Memory-efficient
 * - Thread-safe
 */
public class TacticsCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000; // 5 minutes
    
    private final Map<String, CachedData> cache = new Object2ObjectOpenHashMap<>();
    private final long ttlMs;
    
    public TacticsCache() {
        this(DEFAULT_TTL_MS);
    }
    
    public TacticsCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }
    
    /**
     * Get cached tactics data
     * 
     * @return Cached data if valid, null if expired or not cached
     */
    public synchronized Map<String, Object> get(String key) {
        CachedData cached = cache.get(key);
        if (cached == null) {
            LOGGER.debug("Cache miss: {}", key);
            return null;
        }
        
        long age = System.currentTimeMillis() - cached.timestamp;
        if (age > ttlMs) {
            LOGGER.debug("Cache expired: {} (age: {}ms)", key, age);
            cache.remove(key);
            return null;
        }
        
        LOGGER.debug("Cache hit: {} (age: {}ms)", key, age);
        return cached.data;
    }
    
    /**
     * Store tactics data in cache
     */
    public synchronized void put(String key, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            LOGGER.debug("Not caching empty data for: {}", key);
            return;
        }
        
        cache.put(key, new CachedData(data, System.currentTimeMillis()));
        LOGGER.debug("Cached data for: {} ({} entries)", key, data.size());
    }
    
    /**
     * Clear cache
     */
    public synchronized void clear() {
        int size = cache.size();
        cache.clear();
        LOGGER.info("Cleared cache ({} entries)", size);
    }
    
    /**
     * Get cache statistics
     */
    public synchronized CacheStats getStats() {
        return new CacheStats(cache.size(), ttlMs);
    }
    
    /**
     * Invalidate specific key
     */
    public synchronized void invalidate(String key) {
        if (cache.remove(key) != null) {
            LOGGER.debug("Invalidated cache for: {}", key);
        }
    }
    
    /**
     * Cached data wrapper
     */
    private static class CachedData {
        final Map<String, Object> data;
        final long timestamp;
        
        CachedData(Map<String, Object> data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStats {
        public final int entries;
        public final long ttlMs;
        
        CacheStats(int entries, long ttlMs) {
            this.entries = entries;
            this.ttlMs = ttlMs;
        }
        
        @Override
        public String toString() {
            return String.format("Cache: %d entries, TTL: %dms", entries, ttlMs);
        }
    }
}
