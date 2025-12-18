package com.minecraft.gancity.ai;

import net.minecraft.world.entity.Mob;

/**
 * Lightweight bridge between mixin and AI system
 * Prevents heavy ML classes from loading during mixin discovery
 */
public class AIBridge {
    
    /**
     * Select action for mob without loading heavy ML classes during construction
     */
    public static String selectAction(Mob mob, float mobHealth, float targetHealth, float distance, String mobType) {
        try {
            // Lazy load MobBehaviorAI only when method is called (not during mixin discovery)
            var behaviorAI = com.minecraft.gancity.GANCityMod.getMobBehaviorAI();
            if (behaviorAI == null) return "straight_charge";
            
            var state = new com.minecraft.gancity.ai.MobBehaviorAI.MobState(mobHealth, targetHealth, distance);
            return behaviorAI.selectMobAction(mobType, state);
        } catch (Exception e) {
            return "straight_charge"; // Fallback
        }
    }
    
    /**
     * Start combat episode tracking
     */
    public static void startCombatEpisode(String mobId, String mobType, int tickCount) {
        try {
            var behaviorAI = com.minecraft.gancity.GANCityMod.getMobBehaviorAI();
            if (behaviorAI != null) {
                behaviorAI.startCombatEpisode(mobId, mobType, tickCount);
            }
        } catch (Exception ignored) {}
    }
    
    /**
     * End combat episode tracking
     */
    public static void endCombatEpisode(String mobId, boolean targetKilled, boolean mobKilled, 
                                       int tickCount, String playerId) {
        try {
            var behaviorAI = com.minecraft.gancity.GANCityMod.getMobBehaviorAI();
            if (behaviorAI != null) {
                behaviorAI.endCombatEpisode(mobId, targetKilled, mobKilled, tickCount, playerId);
            }
        } catch (Exception ignored) {}
    }
    
    /**
     * Record combat outcome
     */
    public static void recordCombatOutcome(String mobId, String mobType, String action,
                                          float initialMobHealth, float initialTargetHealth,
                                          float finalMobHealth, float finalTargetHealth,
                                          float distance, int combatTicks, boolean targetKilled,
                                          boolean mobKilled) {
        // Combat outcomes are tracked via episodes, no need for duplicate recording
    }
}
