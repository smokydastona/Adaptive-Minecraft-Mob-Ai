package com.minecraft.gancity.mca;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Integration with MCA Reborn mod for spawning villagers and managing villages
 */
public class MCAIntegration {
    
    private static boolean mcaLoaded = false;
    
    /**
     * Check if MCA Reborn is loaded
     */
    public static boolean isMCALoaded() {
        return mcaLoaded;
    }
    
    /**
     * Set MCA loaded status (called during mod initialization)
     */
    public static void setMCALoaded(boolean loaded) {
        mcaLoaded = loaded;
    }
    
    /**
     * Spawn an MCA villager in the generated building
     * This will be implemented to spawn MCA villagers with proper home assignments
     */
    public static void spawnVillagerInBuilding(Level level, BlockPos buildingPos, int buildingSize) {
        if (!mcaLoaded) {
            return;
        }
        
        // Find a good spawn position (ground level inside the building)
        BlockPos spawnPos = findSpawnPosition(level, buildingPos, buildingSize);
        
        if (spawnPos != null) {
            // Spawn MCA villager using reflection to avoid hard dependency
            try {
                spawnMCAVillager(level, spawnPos, buildingPos);
            } catch (Exception e) {
                // Fallback if MCA API changes
                System.err.println("Failed to spawn MCA villager: " + e.getMessage());
            }
        }
    }
    
    /**
     * Find a suitable spawn position inside the building
     */
    private static BlockPos findSpawnPosition(Level level, BlockPos buildingPos, int size) {
        // Try to find a valid floor position in the center of the building
        int centerX = buildingPos.getX() + size / 2;
        int centerZ = buildingPos.getZ() + size / 2;
        
        for (int y = buildingPos.getY(); y < buildingPos.getY() + size; y++) {
            BlockPos pos = new BlockPos(centerX, y, centerZ);
            BlockPos above = pos.above();
            BlockPos below = pos.below();
            
            // Check if it's a valid spawn position (solid block below, air above)
            if (!level.getBlockState(below).isAir() && 
                level.getBlockState(pos).isAir() && 
                level.getBlockState(above).isAir()) {
                return pos;
            }
        }
        
        return null;
    }
    
    /**
     * Spawn an MCA villager using reflection
     */
    private static void spawnMCAVillager(Level level, BlockPos spawnPos, BlockPos homePos) throws Exception {
        // Use reflection to avoid hard dependency on MCA
        // This allows the mod to work even if MCA isn't installed
        
        try {
            // Try to get MCA's villager entity class
            Class<?> villagerEntityClass = Class.forName("mca.entity.VillagerEntityMCA");
            
            // Create new villager instance
            Object villager = villagerEntityClass.getConstructor(Level.class)
                .newInstance(level);
            
            // Set position
            villagerEntityClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(villager, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            
            // Set home position if MCA supports it
            try {
                villagerEntityClass.getMethod("setHome", BlockPos.class)
                    .invoke(villager, homePos);
            } catch (NoSuchMethodException e) {
                // Home setting might use a different method
            }
            
            // Add to world
            level.addFreshEntity((net.minecraft.world.entity.Entity) villager);
            
        } catch (ClassNotFoundException e) {
            // MCA might not be installed or uses different package structure
            throw new Exception("MCA Reborn not found or incompatible version");
        }
    }
    
    /**
     * Create a village marker for MCA to recognize this as a village
     */
    public static void markAsVillage(Level level, BlockPos centerPos, int villageSize) {
        if (!mcaLoaded) {
            return;
        }
        
        // MCA may have village management systems
        // This would integrate with them if available
    }
    
    /**
     * Get recommended building size for MCA villager houses
     */
    public static int getRecommendedHouseSize() {
        return 8; // 8x8x8 is a good size for villager homes
    }
    
    /**
     * Check if a building is suitable for MCA villagers
     */
    public static boolean isBuildingSuitable(Level level, BlockPos buildingPos, int size) {
        // Check if building has floor, walls, and isn't completely solid
        int airBlocks = 0;
        int solidBlocks = 0;
        
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    BlockPos pos = buildingPos.offset(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        airBlocks++;
                    } else {
                        solidBlocks++;
                    }
                }
            }
        }
        
        // Building should have both air (interior) and solid blocks (walls/floor)
        // Ratio should be roughly 30-70% air
        int totalBlocks = airBlocks + solidBlocks;
        float airRatio = (float) airBlocks / totalBlocks;
        
        return airRatio >= 0.3f && airRatio <= 0.7f;
    }
}
