package com.minecraft.gancity.ai;

import com.minecraft.gancity.GANCityMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraftforge.fml.ModList;

import java.util.EnumSet;

/**
 * AI-Enhanced Melee Attack Goal - standalone version without mixins
 * Uses machine learning to select attack patterns
 * Works via EntityJoinLevelEvent instead of mixin injection
 */
public class EnhancedMeleeGoal extends Goal {
    private final Mob mob;
    private final double speedModifier;
    private final boolean followingTargetEvenIfNotSeen;
    private final boolean enableEnvironmentalTactics;
    private final boolean isVillager;
    private LivingEntity target;
    private int ticksUntilNextAction;
    private int ticksUntilNextAIUpdate;
    private String currentAction = "straight_charge";
    private final MobBehaviorAI behaviorAI;
    private final String mobId;
    private String persistentProfile = null;
    private float initialMobHealth;
    private float initialTargetHealth;
    private int combatTicks = 0;
    
    private static final int AI_UPDATE_INTERVAL = 20;
    
    public EnhancedMeleeGoal(Mob mob, double speedModifier, boolean followEvenIfNotSeen, 
                           boolean enableEnvironmental, boolean isVillager) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.followingTargetEvenIfNotSeen = followEvenIfNotSeen;
        this.enableEnvironmentalTactics = enableEnvironmental;
        this.isVillager = isVillager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.behaviorAI = GANCityMod.getMobBehaviorAI();
        this.mobId = mob.getUUID().toString();
        
        if (isVillager) {
            this.persistentProfile = loadOrCreatePersistentProfile();
        }
    }
    
    private String loadOrCreatePersistentProfile() {
        try {
            net.minecraft.nbt.CompoundTag persistentData = mob.getPersistentData();
            if (persistentData.contains("MCA_AI_Profile")) {
                return persistentData.getString("MCA_AI_Profile");
            }
            
            String[] profiles = {"aggressive_guard", "defensive_guard", "tactical_guard", 
                                "cautious_defender", "berserker_guard", "strategic_defender"};
            String newProfile = profiles[mob.getRandom().nextInt(profiles.length)];
            persistentData.putString("MCA_AI_Profile", newProfile);
            return newProfile;
        } catch (Exception e) {
            return "defensive_guard";
        }
    }
    
    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        this.target = target;
        return true;
    }
    
    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (!this.followingTargetEvenIfNotSeen) {
            return !this.mob.getNavigation().isDone();
        }
        return this.mob.isWithinRestriction(target.blockPosition());
    }
    
    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.target, this.speedModifier);
        this.ticksUntilNextAction = 0;
        this.ticksUntilNextAIUpdate = AI_UPDATE_INTERVAL;
        this.combatTicks = 0;
        this.initialMobHealth = mob.getHealth() / mob.getMaxHealth();
        this.initialTargetHealth = target.getHealth() / target.getMaxHealth();
        
        behaviorAI.startCombatSequence(mobId);
        String mobType = mob.getType().getDescription().getString().toLowerCase();
        behaviorAI.startCombatEpisode(mobId, mobType, mob.tickCount);
        selectNextAction();
    }
    
    @Override
    public void stop() {
        if (this.target != null) {
            recordCombatOutcome();
            String mobType = mob.getType().getDescription().getString().toLowerCase();
            String outcome = determineOutcome();
            behaviorAI.endCombatSequence(mobId, mobType, outcome);
            
            boolean mobKilled = !mob.isAlive();
            boolean targetKilled = !target.isAlive();
            String playerId = (target instanceof net.minecraft.world.entity.player.Player) 
                ? target.getUUID().toString() 
                : "npc";
            behaviorAI.endCombatEpisode(mobId, targetKilled, mobKilled, mob.tickCount, playerId);
        }
        
        this.target = null;
        this.mob.getNavigation().stop();
        this.combatTicks = 0;
    }
    
    private String determineOutcome() {
        if (!mob.isAlive()) return "died";
        if (target != null && !target.isAlive()) return "success";
        return "disengaged";
    }
    
    @Override
    public void tick() {
        if (this.target == null) return;
        
        combatTicks++;
        this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
        
        if (combatTicks % 10 == 0 && target instanceof net.minecraft.world.entity.player.Player) {
            behaviorAI.recordTacticalSample(mobId, mob, (net.minecraft.world.entity.player.Player) target, 0);
        }
        
        if (--this.ticksUntilNextAIUpdate <= 0) {
            if (--this.ticksUntilNextAction <= 0) {
                selectNextAction();
                this.ticksUntilNextAction = 20 + mob.getRandom().nextInt(20);
            }
            this.ticksUntilNextAIUpdate = AI_UPDATE_INTERVAL;
        }
        
        executeAction();
    }
    
    private void recordCombatOutcome() {
        if (behaviorAI == null || target == null) return;
        
        MobBehaviorAI.MobState finalState = new MobBehaviorAI.MobState(
            mob.getHealth() / mob.getMaxHealth(),
            target.getHealth() / target.getMaxHealth(),
            (float) mob.distanceTo(target)
        );
        finalState.combatTime = combatTicks / 20.0f;
        finalState.isNight = !mob.level().isDay();
        finalState.biome = mob.level().getBiome(mob.blockPosition()).toString();
        
        boolean mobDied = !mob.isAlive();
        boolean playerDied = !target.isAlive();
        behaviorAI.recordCombatOutcome(mobId, playerDied, mobDied, finalState, 0.0f, 0.0f, mob);
    }
    
    private void selectNextAction() {
        if (target == null) return;
        
        MobBehaviorAI.MobState state = new MobBehaviorAI.MobState(
            mob.getHealth() / mob.getMaxHealth(),
            target.getHealth() / target.getMaxHealth(),
            (float) mob.distanceTo(target)
        );
        
        state.isNight = !mob.level().isDay();
        state.biome = mob.level().getBiome(mob.blockPosition()).toString();
        state.combatTime = combatTicks / 20.0f;
        
        if (mob instanceof Spider) {
            state.canClimbWalls = true;
        }
        
        String mobType;
        if (isVillager && persistentProfile != null) {
            mobType = persistentProfile;
        } else {
            mobType = mob.getClass().getSimpleName().toLowerCase();
        }
        
        String previousAction = currentAction;
        currentAction = behaviorAI.selectMobActionWithEntity(mobType, state, mobId, mob);
        
        if (previousAction != null && !previousAction.equals(currentAction)) {
            double reward = calculateActionReward();
            behaviorAI.trackActionInSequence(mobId, previousAction, reward);
        }
    }
    
    private double calculateActionReward() {
        double reward = 0.0;
        
        if (target != null) {
            float currentTargetHealth = target.getHealth() / target.getMaxHealth();
            if (currentTargetHealth < initialTargetHealth) {
                reward += (initialTargetHealth - currentTargetHealth) * 10.0;
                initialTargetHealth = currentTargetHealth;
            }
        }
        
        float currentMobHealth = mob.getHealth() / mob.getMaxHealth();
        if (currentMobHealth < initialMobHealth) {
            reward -= (initialMobHealth - currentMobHealth) * 5.0;
            initialMobHealth = currentMobHealth;
        }
        
        reward += 0.1;
        return reward;
    }
    
    private void executeAction() {
        if (target == null) return;
        
        double distance = mob.distanceTo(target);
        double baseSpeed = speedModifier;
        
        switch (currentAction) {
            case "straight_charge":
                mob.getNavigation().moveTo(target, baseSpeed * 1.2);
                break;
            case "circle_strafe":
                circleAroundTarget(baseSpeed);
                break;
            case "kite_backward":
                if (distance < 8.0) retreatFromTarget(baseSpeed * 1.1);
                break;
            case "ambush":
                if (distance > 5.0) mob.getNavigation().stop();
                else mob.getNavigation().moveTo(target, baseSpeed * 1.5);
                break;
            case "group_rush":
            case "suicide_rush":
                mob.getNavigation().moveTo(target, baseSpeed * 1.3);
                break;
            case "retreat_reload":
            case "fake_retreat":
                if (mob.getRandom().nextFloat() < 0.7f) retreatFromTarget(baseSpeed);
                break;
            default:
                mob.getNavigation().moveTo(target, baseSpeed);
                break;
        }
        
        if (distance <= mob.getBbWidth() * 2.0F + target.getBbWidth()) {
            mob.doHurtTarget(target);
        }
    }
    
    private void circleAroundTarget(double speed) {
        if (target == null) return;
        
        double angle = Math.atan2(mob.getZ() - target.getZ(), mob.getX() - target.getX());
        double circleAngle = angle + Math.PI / 4;
        double distance = 4.0;
        double targetX = target.getX() + Math.cos(circleAngle) * distance;
        double targetZ = target.getZ() + Math.sin(circleAngle) * distance;
        
        mob.getNavigation().moveTo(targetX, target.getY(), targetZ, speed);
    }
    
    private void retreatFromTarget(double speed) {
        if (target == null) return;
        
        double angle = Math.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX());
        double distance = 5.0;
        double targetX = mob.getX() - Math.cos(angle) * distance;
        double targetZ = mob.getZ() - Math.sin(angle) * distance;
        
        mob.getNavigation().moveTo(targetX, mob.getY(), targetZ, speed);
    }
}
