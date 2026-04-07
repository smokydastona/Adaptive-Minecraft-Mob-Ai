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
    private static Method getCuriosInventoryMethod = null;
    private static Method getEquippedCuriosMethod = null;
    private static Method itemHandlerGetSlotsMethod = null;
    private static Method itemHandlerGetStackInSlotMethod = null;
    
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
            Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
            Class<?> curiosHandlerClass = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> itemHandlerClass = Class.forName("net.minecraftforge.items.IItemHandlerModifiable");

            getCuriosInventoryMethod = curiosApiClass.getMethod("getCuriosInventory", livingEntityClass);
            getEquippedCuriosMethod = curiosHandlerClass.getMethod("getEquippedCurios");
            itemHandlerGetSlotsMethod = itemHandlerClass.getMethod("getSlots");
            itemHandlerGetStackInSlotMethod = itemHandlerClass.getMethod("getStackInSlot", int.class);
            
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
        return new ArrayList<>(getCurioData(player).equippedCurios);
    }
    
    /**
     * Check if player has specific curio type equipped
     */
    public static boolean hasCurioType(Player player, String curioType) {
        if (curioType == null || curioType.isBlank()) {
            return false;
        }

        String normalizedType = curioType.toLowerCase(Locale.ROOT);
        for (ItemStack curio : getCurioData(player).equippedCurios) {
            String itemName = curio.getDescriptionId().toLowerCase(Locale.ROOT);
            if (itemName.contains(normalizedType)) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Get total armor/protection value including curios
     */
    public static float getTotalProtectionWithCurios(Player player) {
        CachedCurioData curioData = getCurioData(player);
        return player.getArmorValue() + curioData.protectionBonus;
    }
    
    /**
     * Check if player has magical trinkets (affects AI tactics)
     */
    public static boolean hasMagicalTrinkets(Player player) {
        return getCurioData(player).hasMagicalTrinkets;
    }
    
    /**
     * Get Curio enhancement factor (1.0 = no curios, >1.0 = enhanced player)
     */
    public static float getCurioEnhancementFactor(Player player) {
        return getCurioData(player).enhancement;
    }

    private static CachedCurioData getCurioData(Player player) {
        if (!initialized) {
            init();
            if (!initialized) {
                return CachedCurioData.empty();
            }
        }

        UUID playerId = player.getUUID();
        CachedCurioData cached = curioCache.get(playerId);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.timestamp) <= CACHE_DURATION_MS) {
            return cached;
        }

        CachedCurioData refreshed = loadCurioData(player, now);
        updateCache(playerId, refreshed);
        return refreshed;
    }

    private static CachedCurioData loadCurioData(Player player, long now) {
        List<ItemStack> curios = new ArrayList<>();

        try {
            Object optionalInventory = getCuriosInventoryMethod.invoke(null, player);
            if (!(optionalInventory instanceof Optional<?> inventoryOptional) || inventoryOptional.isEmpty()) {
                return CachedCurioData.empty(now);
            }

            Object handler = inventoryOptional.get();
            Object itemHandler = getEquippedCuriosMethod.invoke(handler);
            if (itemHandler == null) {
                return CachedCurioData.empty(now);
            }

            int slots = ((Number) itemHandlerGetSlotsMethod.invoke(itemHandler)).intValue();
            for (int index = 0; index < slots; index++) {
                Object stackObject = itemHandlerGetStackInSlotMethod.invoke(itemHandler, index);
                if (stackObject instanceof ItemStack stack && !stack.isEmpty()) {
                    curios.add(stack.copy());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error getting Curios items: {}", e.getMessage());
            return CachedCurioData.empty(now);
        }

        return new CachedCurioData(
            Collections.unmodifiableList(curios),
            hasMagicalCurio(curios),
            getEnhancementForCount(curios.size()),
            estimateProtectionBonus(curios),
            now
        );
    }

    private static boolean hasMagicalCurio(List<ItemStack> curios) {
        for (ItemStack curio : curios) {
            String itemName = curio.getDescriptionId().toLowerCase(Locale.ROOT);
            if (itemName.contains("ring") || itemName.contains("charm") ||
                itemName.contains("amulet") || itemName.contains("talisman") ||
                itemName.contains("relic") || itemName.contains("bauble")) {
                return true;
            }
        }
        return false;
    }

    private static float getEnhancementForCount(int curioCount) {
        if (curioCount <= 0) {
            return 1.0f;
        }
        if (curioCount <= 2) {
            return 1.1f;
        }
        if (curioCount <= 4) {
            return 1.2f;
        }
        return 1.3f;
    }

    private static float estimateProtectionBonus(List<ItemStack> curios) {
        float protectionBonus = 0.0f;

        for (ItemStack curio : curios) {
            String itemName = curio.getDescriptionId().toLowerCase(Locale.ROOT);
            if (itemName.contains("protection") || itemName.contains("defense") ||
                itemName.contains("shield") || itemName.contains("ward") ||
                itemName.contains("guard") || itemName.contains("armor") ||
                itemName.contains("armour") || itemName.contains("resist")) {
                protectionBonus += 1.0f;
            } else if (itemName.contains("ring") || itemName.contains("amulet") ||
                itemName.contains("charm") || itemName.contains("talisman")) {
                protectionBonus += 0.25f;
            }
        }

        return protectionBonus;
    }
    
    /**
     * Update cache with curio data
     */
    private static void updateCache(UUID playerId, CachedCurioData curioData) {
        // Evict old entries if cache too large
        if (curioCache.size() > MAX_CACHE_SIZE) {
            long now = System.currentTimeMillis();
            curioCache.entrySet().removeIf(entry -> 
                (now - entry.getValue().timestamp) > CACHE_DURATION_MS * 2
            );
        }
        
        curioCache.put(playerId, curioData);
    }
    
    private static class CachedCurioData {
        final List<ItemStack> equippedCurios;
        final boolean hasMagicalTrinkets;
        final float enhancement;
        final float protectionBonus;
        final long timestamp;
        
        CachedCurioData(List<ItemStack> equippedCurios, boolean hasTrinkets, float enhancement, float protectionBonus, long timestamp) {
            this.equippedCurios = equippedCurios;
            this.hasMagicalTrinkets = hasTrinkets;
            this.enhancement = enhancement;
            this.protectionBonus = protectionBonus;
            this.timestamp = timestamp;
        }

        static CachedCurioData empty() {
            return empty(System.currentTimeMillis());
        }

        static CachedCurioData empty(long timestamp) {
            return new CachedCurioData(Collections.emptyList(), false, 1.0f, 0.0f, timestamp);
        }
    }
}
