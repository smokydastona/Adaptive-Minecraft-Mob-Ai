package com.minecraft.gancity.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Curios API integration for detecting trinkets, baubles, and accessories
 * Uses reflection to avoid hard dependency
 */
@SuppressWarnings("unused")
public class CuriosIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static boolean initialized = false;
    private static Class<?> curiosApiClass = null;
    private static Method getCuriosHelperMethod = null;
    private static Method getEquippedCuriosMethod = null;
    
    // Cache for frequently called methods
    private static final Map<UUID, CachedCurioData> curioCache = new HashMap<>();
    private static final long CACHE_DURATION_MS = 1000; // 1 second cache
    private static final int MAX_CACHE_SIZE = 50;
    
    /**
     * Initialize Curios integration via reflection
     */
    public static void init() {
        if (!ModCompatibility.isCuriosLoaded()) {
            return;
        }
        
        try {
            // Load Curios API classes via reflection
            curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            getCuriosHelperMethod = curiosApiClass.getMethod("getCuriosHelper");
            
            initialized = true;
            LOGGER.info("Curios API integration initialized successfully");
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize Curios integration: {}", e.getMessage());
            initialized = false;
        }
    }
    
    /**
     * Get all equipped Curio items from player
     */
    public static List<ItemStack> getEquippedCurios(Player player) {
        List<ItemStack> curios = new ArrayList<>();
        
        if (!initialized) {
            init(); // Lazy init on first use to avoid classloading deadlock
            if (!initialized) {
                return curios;
            }
        }
        
        try {
            // Use reflection to get equipped curios
            // Would be: CuriosApi.getCuriosHelper().getEquippedCurios(player).resolve()
            
            // For now, return empty list - full implementation would require Curios as dependency
            // This is a placeholder for when Curios is added to build.gradle
            LOGGER.debug("Checking Curios for player {}", player.getName().getString());
            
        } catch (Exception e) {
            LOGGER.debug("Error getting Curios items: {}", e.getMessage());
        }
        
        return curios;
    }
    
    /**
     * Check if player has specific curio type equipped
     */
    public static boolean hasCurioType(Player player, String curioType) {
        if (!initialized) {
            return false;
        }
        
        try {
            // Check for specific curio types (ring, necklace, charm, etc.)
            List<ItemStack> curios = getEquippedCurios(player);
            
            // Would implement curio type checking here
            return false;
            
        } catch (Exception e) {
            LOGGER.debug("Error checking Curio type: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get total armor/protection value including curios
     */
    public static float getTotalProtectionWithCurios(Player player) {
        float baseProtection = player.getArmorValue();
        
        if (!initialized) {
            return baseProtection;
        }
        
        try {
            List<ItemStack> curios = getEquippedCurios(player);
            
            // Add protection from curio items
            for (ItemStack curio : curios) {
                // Would calculate protection bonus from curios
                // baseProtection += getCurioProtection(curio);
            }
            
        } catch (Exception e) {
            LOGGER.debug("Error calculating Curio protection: {}", e.getMessage());
        }
        
        return baseProtection;
    }
    
    /**
     * Check if player has magical trinkets (affects AI tactics)
     */
    public static boolean hasMagicalTrinkets(Player player) {
        if (!initialized) {
            return false;
        }
        
        try {
            List<ItemStack> curios = getEquippedCurios(player);
            
            // Check for magical curios that would affect combat
            // Ring of Resistance, Charm of Protection, etc.
            for (ItemStack curio : curios) {
                String itemName = curio.getDescriptionId().toLowerCase();
                if (itemName.contains("ring") || itemName.contains("charm") || 
                    itemName.contains("amulet") || itemName.contains("talisman")) {
                    return true;
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("Error checking magical trinkets: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get Curio enhancement factor (1.0 = no curios, >1.0 = enhanced player)
     */
    public static float getCurioEnhancementFactor(Player player) {
        if (!initialized) {
            return 1.0f;
        }
        
        try {
            List<ItemStack> curios = getEquippedCurios(player);
            
            // More curios = tougher player = mobs should be more careful
            if (curios.isEmpty()) {
                return 1.0f;
            } else if (curios.size() <= 2) {
                return 1.1f;
            } else if (curios.size() <= 4) {
                return 1.2f;
            } else {
                return 1.3f;  // Heavily equipped player
            }
            
        } catch (Exception e) {
            LOGGER.debug("Error calculating Curio enhancement: {}", e.getMessage());
        }
        
        return 1.0f;
    }
    
    /**
     * Update cache with curio data
     */
    private static void updateCache(UUID playerId, boolean hasTrinkets, float enhancement) {
        // Evict old entries if cache too large
        if (curioCache.size() > MAX_CACHE_SIZE) {
            long now = System.currentTimeMillis();
            curioCache.entrySet().removeIf(entry -> 
                (now - entry.getValue().timestamp) > CACHE_DURATION_MS * 2
            );
        }
        
        curioCache.put(playerId, new CachedCurioData(hasTrinkets, enhancement, System.currentTimeMillis()));
    }
    
    private static class CachedCurioData {
        final boolean hasMagicalTrinkets;
        final float enhancement;
        final long timestamp;
        
        CachedCurioData(boolean hasTrinkets, float enhancement, long timestamp) {
            this.hasMagicalTrinkets = hasTrinkets;
            this.enhancement = enhancement;
            this.timestamp = timestamp;
        }
    }
}
