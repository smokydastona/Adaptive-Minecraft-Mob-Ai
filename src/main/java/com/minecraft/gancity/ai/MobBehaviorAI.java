package com.minecraft.gancity.ai;

import com.minecraft.gancity.ml.MobLearningModel;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

/**
 * AI system for enhancing mob behavior using reinforcement learning
 * Learns from combat encounters to adapt mob attack patterns
 */
public class MobBehaviorAI {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private MobLearningModel learningModel;
    private boolean mlEnabled = false;
    private final Map<String, MobBehaviorProfile> behaviorProfiles = new HashMap<>();
    private final Map<String, MobState> lastStateCache = new HashMap<>();
    private final Map<String, String> lastActionCache = new HashMap<>();
    private final Random random = new Random();
    private float difficultyMultiplier = 1.0f;

    public MobBehaviorAI() {
        initializeDefaultProfiles();
        initializeMLModel();
    }

    /**
     * Initialize the machine learning model
     */
    private void initializeMLModel() {
        try {
            learningModel = new MobLearningModel();
            mlEnabled = true;
            LOGGER.info("Mob behavior ML model initialized - progressive learning enabled");
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize ML model, using rule-based fallback: {}", e.getMessage());
            mlEnabled = false;
        }
    }

    /**
     * Set difficulty multiplier (affects learning speed and exploration)
     */
    public void setDifficultyMultiplier(float multiplier) {
        this.difficultyMultiplier = Math.max(0.1f, Math.min(5.0f, multiplier));
    }

    /**
     * Initialize default behavior profiles for different mob types
     */
    private void initializeDefaultProfiles() {
        // Zombie behaviors
        behaviorProfiles.put("zombie", new MobBehaviorProfile(
            "zombie",
            Arrays.asList("straight_charge", "circle_strafe", "group_rush"),
            0.7f  // aggression level
        ));
        
        // Skeleton behaviors
        behaviorProfiles.put("skeleton", new MobBehaviorProfile(
            "skeleton",
            Arrays.asList("kite_backward", "find_high_ground", "strafe_shoot", "retreat_reload"),
            0.5f
        ));
        
        // Creeper behaviors
        behaviorProfiles.put("creeper", new MobBehaviorProfile(
            "creeper",
            Arrays.asList("ambush", "stealth_approach", "fake_retreat", "suicide_rush"),
            0.8f
        ));
        
        // Spider behaviors
        behaviorProfiles.put("spider", new MobBehaviorProfile(
            "spider",
            Arrays.asList("wall_climb_attack", "ceiling_drop", "web_trap", "leap_attack"),
            0.6f
        ));
        
        LOGGER.info("Initialized behavior profiles for {} mob types", behaviorProfiles.size());
    }

    /**
     * Select next action for a mob based on current state
     * Uses ML model if enabled, otherwise rule-based
     */
    public String selectMobAction(String mobType, MobState state) {
        return selectMobAction(mobType, state, UUID.randomUUID().toString());
    }
    
    /**
     * Select next action for a specific mob instance
     */
    public String selectMobAction(String mobType, MobState state, String mobId) {
        MobBehaviorProfile profile = behaviorProfiles.get(mobType.toLowerCase());
        
        if (profile == null) {
            return "default_attack";
        }

        String selectedAction;
        
        if (mlEnabled && learningModel != null) {
            // Use neural network for action selection
            selectedAction = selectActionWithML(profile, state);
        } else {
            // Use rule-based system
            selectedAction = selectActionRuleBased(profile, state);
        }
        
        // Cache state and action for learning when outcome is recorded
        lastStateCache.put(mobId, state.copy());
        lastActionCache.put(mobId, selectedAction);
        
        return selectedAction;
    }

    /**
     * ML-based action selection using neural network
     */
    private String selectActionWithML(MobBehaviorProfile profile, MobState state) {
        List<String> validActions = getValidActions(profile, state);
        if (validActions.isEmpty()) {
            return "default_attack";
        }
        
        // Convert state to feature vector for neural network
        float[] stateFeatures = stateToFeatureVector(state);
        
        // Use ML model to select action
        String selectedAction = learningModel.selectAction(stateFeatures, validActions);
        
        // Record for learning
        profile.recordAction(selectedAction, state);
        
        return selectedAction;
    }
    
    /**
     * Convert MobState to feature vector for neural network input
     */
    private float[] stateToFeatureVector(MobState state) {
        return new float[] {
            state.health,                                    // 0: Mob health (0-1)
            state.targetHealth,                              // 1: Player health (0-1)
            state.distanceToTarget / 20.0f,                  // 2: Distance normalized
            state.hasHighGround ? 1.0f : 0.0f,              // 3: Height advantage
            state.canClimbWalls ? 1.0f : 0.0f,              // 4: Climbing ability
            state.nearbyAlliesCount / 10.0f,                // 5: Nearby allies normalized
            state.isNight ? 1.0f : 0.0f,                    // 6: Time of day
            state.biome.hashCode() % 100 / 100.0f,          // 7: Biome encoding
            difficultyMultiplier / 3.0f,                    // 8: Difficulty setting
            state.combatTime / 100.0f                       // 9: Combat duration
        };
    }
    
    /**
     * Get list of valid actions for current state
     */
    private List<String> getValidActions(MobBehaviorProfile profile, MobState state) {
        List<String> actions = profile.getActions();
        List<String> validActions = new ArrayList<>();
        
        for (String action : actions) {
            if (isActionValid(action, state)) {
                validActions.add(action);
            }
        }
        
        return validActions.isEmpty() ? actions : validActions;
    }

    /**
     * Rule-based action selection with adaptive behavior
     */
    private String selectActionRuleBased(MobBehaviorProfile profile, MobState state) {
        List<String> actions = profile.getActions();
        
        // Filter actions based on state
        List<String> validActions = new ArrayList<>();
        
        for (String action : actions) {
            if (isActionValid(action, state)) {
                validActions.add(action);
            }
        }
        
        if (validActions.isEmpty()) {
            return "default_attack";
        }
        
        // Weight actions based on situation
        String selectedAction = weightedActionSelection(validActions, state, profile);
        
        // Learn from this decision
        profile.recordAction(selectedAction, state);
        
        return selectedAction;
    }

    /**
     * Check if an action is valid in current state
     */
    private boolean isActionValid(String action, MobState state) {
        switch (action) {
            case "kite_backward":
            case "retreat_reload":
                return state.distanceToTarget > 3.0f;
            case "suicide_rush":
            case "group_rush":
                return state.distanceToTarget < 10.0f;
            case "find_high_ground":
                return !state.hasHighGround;
            case "ceiling_drop":
            case "wall_climb_attack":
                return state.canClimbWalls;
            default:
                return true;
        }
    }

    /**
     * Select action with weighted probability based on state
     */
    private String weightedActionSelection(List<String> actions, MobState state, MobBehaviorProfile profile) {
        Map<String, Float> weights = new HashMap<>();
        
        for (String action : actions) {
            float weight = calculateActionWeight(action, state, profile);
            weights.put(action, weight);
        }
        
        // Weighted random selection
        float totalWeight = weights.values().stream().reduce(0f, Float::sum);
        float randomValue = random.nextFloat() * totalWeight;
        
        float currentWeight = 0f;
        for (Map.Entry<String, Float> entry : weights.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue <= currentWeight) {
                return entry.getKey();
            }
        }
        
        return actions.get(0);
    }

    /**
     * Calculate weight for an action based on current state
     */
    private float calculateActionWeight(String action, MobState state, MobBehaviorProfile profile) {
        float baseWeight = 1.0f;
        
        // Adjust weight based on player health
        if (state.targetHealth < 0.3f) {
            // Player is low health - aggressive actions
            if (action.contains("rush") || action.contains("charge")) {
                baseWeight *= 2.0f;
            }
        }
        
        // Adjust based on mob health
        if (state.health < 0.3f) {
            // Mob is low health - defensive actions
            if (action.contains("retreat") || action.contains("kite")) {
                baseWeight *= 2.0f;
            }
        }
        
        // Adjust based on distance
        if (state.distanceToTarget < 3.0f) {
            if (action.contains("melee") || action.contains("rush")) {
                baseWeight *= 1.5f;
            }
        } else if (state.distanceToTarget > 8.0f) {
            if (action.contains("range") || action.contains("approach")) {
                baseWeight *= 1.5f;
            }
        }
        
        // Factor in past success rate
        baseWeight *= profile.getActionSuccessRate(action);
        
        return baseWeight;
    }

    /**
     * Record combat outcome to improve AI over time
     * This triggers the ML model to learn from experience
     */
    public void recordCombatOutcome(String mobId, boolean playerDied, boolean mobDied, MobState finalState) {
        // Get cached state and action
        MobState initialState = lastStateCache.remove(mobId);
        String action = lastActionCache.remove(mobId);
        
        if (initialState == null || action == null) {
            return;  // No cached data for this mob
        }
        
        // Calculate reward based on outcome
        float reward = calculateReward(initialState, finalState, playerDied, mobDied);
        
        // Update ML model if enabled
        if (mlEnabled && learningModel != null) {
            float[] initialFeatures = stateToFeatureVector(initialState);
            float[] finalFeatures = stateToFeatureVector(finalState);
            boolean episodeDone = playerDied || mobDied;
            
            learningModel.addExperience(initialFeatures, action, reward, finalFeatures, episodeDone);
        }
    }
    
    /**
     * Calculate reward for reinforcement learning
     * Positive for successful mob behavior, negative for unsuccessful
     */
    private float calculateReward(MobState initialState, MobState finalState, boolean playerDied, boolean mobDied) {
        float reward = 0.0f;
        
        // Major rewards/penalties for combat outcome
        if (playerDied) {
            reward += 10.0f * difficultyMultiplier;  // Mob won
        } else if (mobDied) {
            reward -= 5.0f;  // Mob lost
        }
        
        // Reward for damaging player
        float playerDamage = initialState.targetHealth - finalState.targetHealth;
        reward += playerDamage * 5.0f;
        
        // Penalty for taking damage
        float mobDamage = initialState.health - finalState.health;
        reward -= mobDamage * 3.0f;
        
        // Small reward for maintaining combat distance
        if (finalState.distanceToTarget > 1.0f && finalState.distanceToTarget < 6.0f) {
            reward += 0.5f;
        }
        
        // Bonus for tactical positioning
        if (finalState.hasHighGround && !initialState.hasHighGround) {
            reward += 1.0f;
        }
        
        return reward * difficultyMultiplier;
    }
    
    /**
     * Save the trained ML model (call on server shutdown)
     */
    public void saveModel() {
        if (mlEnabled && learningModel != null) {
            learningModel.saveModel();
        }
    }
    
    /**
     * Get ML statistics for debugging/display
     */
    public String getMLStats() {
        if (!mlEnabled || learningModel == null) {
            return "ML disabled - using rule-based AI";
        }
        
        return String.format("ML enabled | Training steps: %d | Exploration rate: %.3f | Experiences: %d",
            learningModel.getTrainingSteps(),
            learningModel.getEpsilon(),
            learningModel.getExperienceCount()
        );
    }

    /**
     * Get aggression modifier for a mob type
     */
    public float getAggressionLevel(String mobType) {
        MobBehaviorProfile profile = behaviorProfiles.get(mobType.toLowerCase());
        return profile != null ? profile.getAggressionLevel() : 0.5f;
    }

    /**
     * Represents the current state of a mob during combat
     */
    public static class MobState {
        public float health;
        public float targetHealth;
        public float distanceToTarget;
        public boolean hasHighGround;
        public boolean canClimbWalls;
        public int nearbyAlliesCount;
        public String biome;
        public boolean isNight;
        public float combatTime;  // Seconds in combat

        public MobState(float health, float targetHealth, float distance) {
            this.health = health;
            this.targetHealth = targetHealth;
            this.distanceToTarget = distance;
            this.hasHighGround = false;
            this.canClimbWalls = false;
            this.nearbyAlliesCount = 0;
            this.biome = "plains";
            this.isNight = false;
            this.combatTime = 0.0f;
        }
        
        /**
         * Create a copy of this state (for caching)
         */
        public MobState copy() {
            MobState copy = new MobState(health, targetHealth, distanceToTarget);
            copy.hasHighGround = this.hasHighGround;
            copy.canClimbWalls = this.canClimbWalls;
            copy.nearbyAlliesCount = this.nearbyAlliesCount;
            copy.biome = this.biome;
            copy.isNight = this.isNight;
            copy.combatTime = this.combatTime;
            return copy;
        }
    }

    /**
     * Stores behavior patterns and learning data for a mob type
     */
    private static class MobBehaviorProfile {
        private final String mobType;
        private final List<String> actions;
        private final float aggressionLevel;
        private final Map<String, Integer> actionSuccessCount = new HashMap<>();
        private final Map<String, Integer> actionFailureCount = new HashMap<>();

        public MobBehaviorProfile(String mobType, List<String> actions, float aggression) {
            this.mobType = mobType;
            this.actions = new ArrayList<>(actions);
            this.aggressionLevel = aggression;
            
            // Initialize counters
            for (String action : actions) {
                actionSuccessCount.put(action, 1);
                actionFailureCount.put(action, 1);
            }
        }

        public List<String> getActions() {
            return new ArrayList<>(actions);
        }

        public float getAggressionLevel() {
            return aggressionLevel;
        }

        public void recordAction(String action, MobState state) {
            // Track action usage
        }

        public void recordOutcome(String action, boolean success) {
            if (success) {
                actionSuccessCount.put(action, actionSuccessCount.getOrDefault(action, 0) + 1);
            } else {
                actionFailureCount.put(action, actionFailureCount.getOrDefault(action, 0) + 1);
            }
        }

        public float getActionSuccessRate(String action) {
            int successes = actionSuccessCount.getOrDefault(action, 1);
            int failures = actionFailureCount.getOrDefault(action, 1);
            return (float) successes / (successes + failures);
        }
    }
}
