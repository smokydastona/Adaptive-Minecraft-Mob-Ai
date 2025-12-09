package com.minecraft.gancity.ai;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.*;

/**
 * Autonomous Goal Assignment - Self-directed mob behavior
 * Inspired by AI-Player's self-goal assignment system
 * 
 * Mobs autonomously choose goals when idle:
 * - Patrol territory
 * - Scout for threats
 * - Find tactical positions
 * - Call reinforcements
 */
public class AutonomousGoals {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Goal assignment probabilities
    private static final float GOAL_ASSIGNMENT_CHANCE = 0.15f; // 15% chance per check
    private static final long GOAL_CHECK_INTERVAL = 5000; // Check every 5 seconds
    
    private final Map<UUID, ActiveGoal> activeGoals = new HashMap<>();
    private final Map<String, List<GoalTemplate>> goalsByMobType = new HashMap<>();
    private final Random random = new Random();
    
    public AutonomousGoals() {
        initializeGoalTemplates();
    }
    
    /**
     * Initialize goal templates for different mob types
     */
    private void initializeGoalTemplates() {
        // Skeleton goals - ranged tactical behavior
        List<GoalTemplate> skeletonGoals = Arrays.asList(
            new GoalTemplate("find_high_ground", 0.4f, GoalPriority.MEDIUM, 15000),
            new GoalTemplate("patrol_area", 0.3f, GoalPriority.LOW, 20000),
            new GoalTemplate("scout_enemies", 0.2f, GoalPriority.HIGH, 10000),
            new GoalTemplate("ambush_position", 0.1f, GoalPriority.MEDIUM, 25000)
        );
        goalsByMobType.put("skeleton", skeletonGoals);
        
        // Zombie goals - aggressive pack behavior
        List<GoalTemplate> zombieGoals = Arrays.asList(
            new GoalTemplate("call_horde", 0.3f, GoalPriority.HIGH, 8000),
            new GoalTemplate("surround_target", 0.3f, GoalPriority.MEDIUM, 12000),
            new GoalTemplate("break_defenses", 0.2f, GoalPriority.MEDIUM, 15000),
            new GoalTemplate("patrol_village", 0.2f, GoalPriority.LOW, 18000)
        );
        goalsByMobType.put("zombie", zombieGoals);
        
        // Creeper goals - stealth and positioning
        List<GoalTemplate> creeperGoals = Arrays.asList(
            new GoalTemplate("stealth_approach", 0.5f, GoalPriority.HIGH, 10000),
            new GoalTemplate("find_cover", 0.2f, GoalPriority.MEDIUM, 8000),
            new GoalTemplate("wait_ambush", 0.2f, GoalPriority.MEDIUM, 30000),
            new GoalTemplate("coordinate_explosion", 0.1f, GoalPriority.HIGH, 5000)
        );
        goalsByMobType.put("creeper", creeperGoals);
        
        // Spider goals - climbing and flanking
        List<GoalTemplate> spiderGoals = Arrays.asList(
            new GoalTemplate("climb_walls", 0.4f, GoalPriority.MEDIUM, 10000),
            new GoalTemplate("ceiling_ambush", 0.3f, GoalPriority.HIGH, 20000),
            new GoalTemplate("flank_target", 0.2f, GoalPriority.MEDIUM, 12000),
            new GoalTemplate("web_trap", 0.1f, GoalPriority.LOW, 15000)
        );
        goalsByMobType.put("spider", spiderGoals);
        
        // Enderman goals - teleportation tactics
        List<GoalTemplate> endermanGoals = Arrays.asList(
            new GoalTemplate("teleport_scout", 0.4f, GoalPriority.MEDIUM, 8000),
            new GoalTemplate("gather_blocks", 0.3f, GoalPriority.LOW, 12000),
            new GoalTemplate("hit_and_run", 0.2f, GoalPriority.HIGH, 6000),
            new GoalTemplate("defend_territory", 0.1f, GoalPriority.MEDIUM, 15000)
        );
        goalsByMobType.put("enderman", endermanGoals);
        
        LOGGER.info("Autonomous Goals initialized for {} mob types", goalsByMobType.size());
    }
    
    /**
     * Check if mob should assign itself a new goal
     */
    public boolean shouldAssignGoal(Mob mob) {
        UUID mobId = mob.getUUID();
        
        // Don't assign if already has active goal
        if (hasActiveGoal(mobId)) {
            return false;
        }
        
        // Don't assign if mob is in combat
        if (mob.getTarget() != null) {
            return false;
        }
        
        // Random chance to assign goal
        return random.nextFloat() < GOAL_ASSIGNMENT_CHANCE;
    }
    
    /**
     * Assign autonomous goal to mob
     */
    public ActiveGoal assignGoal(Mob mob) {
        String mobType = getMobType(mob);
        List<GoalTemplate> templates = goalsByMobType.get(mobType);
        
        if (templates == null || templates.isEmpty()) {
            LOGGER.debug("No goals available for mob type: {}", mobType);
            return null;
        }
        
        // Weight-based random selection
        GoalTemplate selected = selectGoalByWeight(templates);
        
        if (selected == null) {
            return null;
        }
        
        // Create active goal
        ActiveGoal goal = new ActiveGoal(
            selected.name,
            selected.priority,
            System.currentTimeMillis() + selected.duration,
            generateGoalContext(mob, selected)
        );
        
        activeGoals.put(mob.getUUID(), goal);
        
        LOGGER.debug("Mob {} assigned autonomous goal: {}", 
            mob.getType().getDescriptionId(), selected.name);
        
        return goal;
    }
    
    /**
     * Get current goal for mob
     */
    public ActiveGoal getCurrentGoal(UUID mobId) {
        return activeGoals.get(mobId);
    }
    
    /**
     * Check if mob has active goal
     */
    public boolean hasActiveGoal(UUID mobId) {
        ActiveGoal goal = activeGoals.get(mobId);
        
        if (goal == null) {
            return false;
        }
        
        // Check if goal expired
        if (System.currentTimeMillis() > goal.expirationTime) {
            activeGoals.remove(mobId);
            return false;
        }
        
        return true;
    }
    
    /**
     * Complete current goal
     */
    public void completeGoal(UUID mobId) {
        ActiveGoal goal = activeGoals.remove(mobId);
        if (goal != null) {
            LOGGER.debug("Goal '{}' completed", goal.name);
        }
    }
    
    /**
     * Cancel current goal (e.g., when entering combat)
     */
    public void cancelGoal(UUID mobId) {
        ActiveGoal goal = activeGoals.remove(mobId);
        if (goal != null) {
            LOGGER.debug("Goal '{}' cancelled", goal.name);
        }
    }
    
    /**
     * Update all active goals
     */
    public void tick() {
        long currentTime = System.currentTimeMillis();
        
        activeGoals.entrySet().removeIf(entry -> {
            ActiveGoal goal = entry.getValue();
            if (currentTime > goal.expirationTime) {
                LOGGER.debug("Goal '{}' expired", goal.name);
                return true;
            }
            return false;
        });
    }
    
    /**
     * Get goal statistics
     */
    public Map<String, Object> getGoalStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_goals", activeGoals.size());
        
        // Count goals by type
        Map<String, Integer> byType = new HashMap<>();
        for (ActiveGoal goal : activeGoals.values()) {
            byType.merge(goal.name, 1, Integer::sum);
        }
        stats.put("goals_by_type", byType);
        
        return stats;
    }
    
    /**
     * Select goal using weighted random selection
     */
    private GoalTemplate selectGoalByWeight(List<GoalTemplate> templates) {
        float totalWeight = 0.0f;
        for (GoalTemplate template : templates) {
            totalWeight += template.weight;
        }
        
        float randValue = random.nextFloat() * totalWeight;
        float cumulative = 0.0f;
        
        for (GoalTemplate template : templates) {
            cumulative += template.weight;
            if (randValue <= cumulative) {
                return template;
            }
        }
        
        return templates.get(0); // Fallback
    }
    
    /**
     * Generate context data for goal
     */
    private Map<String, Object> generateGoalContext(Mob mob, GoalTemplate template) {
        Map<String, Object> context = new HashMap<>();
        
        context.put("start_position", mob.blockPosition());
        context.put("start_time", System.currentTimeMillis());
        context.put("mob_type", getMobType(mob));
        
        // Goal-specific context
        switch (template.name) {
            case "find_high_ground":
                context.put("target_y_level", mob.blockPosition().getY() + 5);
                break;
                
            case "patrol_area":
                context.put("patrol_radius", 20);
                context.put("patrol_center", mob.blockPosition());
                break;
                
            case "scout_enemies":
                context.put("scout_radius", 30);
                break;
                
            case "call_horde":
                context.put("call_radius", 40);
                context.put("min_zombies", 3);
                break;
                
            case "stealth_approach":
                context.put("crouch", true);
                context.put("max_distance", 15);
                break;
        }
        
        return context;
    }
    
    /**
     * Get mob type string
     */
    private String getMobType(Mob mob) {
        String fullType = mob.getType().getDescriptionId();
        
        if (fullType.contains("skeleton")) return "skeleton";
        if (fullType.contains("zombie")) return "zombie";
        if (fullType.contains("creeper")) return "creeper";
        if (fullType.contains("spider")) return "spider";
        if (fullType.contains("enderman")) return "enderman";
        
        return "unknown";
    }
    
    /**
     * Goal template - blueprint for creating goals
     */
    private static class GoalTemplate {
        final String name;
        final float weight;      // Selection probability weight
        final GoalPriority priority;
        final long duration;     // How long goal lasts (ms)
        
        GoalTemplate(String name, float weight, GoalPriority priority, long duration) {
            this.name = name;
            this.weight = weight;
            this.priority = priority;
            this.duration = duration;
        }
    }
    
    /**
     * Active goal instance
     */
    public static class ActiveGoal {
        public final String name;
        public final GoalPriority priority;
        public final long expirationTime;
        public final Map<String, Object> context;
        
        public ActiveGoal(String name, GoalPriority priority, long expirationTime, 
                         Map<String, Object> context) {
            this.name = name;
            this.priority = priority;
            this.expirationTime = expirationTime;
            this.context = context;
        }
        
        public String getName() {
            return name;
        }
        
        public GoalPriority getPriority() {
            return priority;
        }
        
        public Object getContext(String key) {
            return context.get(key);
        }
    }
    
    /**
     * Goal priority levels
     */
    public enum GoalPriority {
        LOW,
        MEDIUM,
        HIGH
    }
}
