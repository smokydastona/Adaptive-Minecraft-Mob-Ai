package com.minecraft.gancity.mixin;

import com.minecraft.gancity.ai.MobBehaviorAI;
import com.minecraft.gancity.GANCityMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;

/**
 * Mixin to enhance mob AI with adaptive behavior
 * Modifies attack patterns based on AI decisions
 */
@Mixin(Mob.class)
public abstract class MobAIEnhancementMixin {
    
    /**
     * Inject AI-enhanced behavior when mob registers goals
     */
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void onRegisterGoals(CallbackInfo ci) {
        Mob mob = (Mob)(Object)this;
        
        // Add AI-enhanced combat goal
        if (mob instanceof Monster) {
            mob.goalSelector.addGoal(2, new AIEnhancedMeleeGoal(mob, 1.0, true));
        }
    }
    
    /**
     * AI-Enhanced Melee Attack Goal
     * Uses machine learning to select attack patterns
     */
    private static class AIEnhancedMeleeGoal extends Goal {
        private final Mob mob;
        private final double speedModifier;
        private final boolean followingTargetEvenIfNotSeen;
        private LivingEntity target;
        private int ticksUntilNextAction;
        private String currentAction = "straight_charge";
        private final MobBehaviorAI behaviorAI;
        private final String mobId;  // Unique ID for this mob instance
        private float initialMobHealth;
        private float initialTargetHealth;
        private int combatTicks = 0;
        
        public AIEnhancedMeleeGoal(Mob mob, double speedModifier, boolean followEvenIfNotSeen) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.followingTargetEvenIfNotSeen = followEvenIfNotSeen;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
            this.behaviorAI = GANCityMod.getMobBehaviorAI();
            this.mobId = mob.getUUID().toString();
        }
        
        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return false;
            } else if (!target.isAlive()) {
                return false;
            } else {
                this.target = target;
                return true;
            }
        }
        
        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return false;
            } else if (!target.isAlive()) {
                return false;
            } else if (!this.followingTargetEvenIfNotSeen) {
                return !this.mob.getNavigation().isDone();
            } else {
                return this.mob.isWithinRestriction(target.blockPosition());
            }
        }
        
        @Override
        public void start() {
            this.mob.getNavigation().moveTo(this.target, this.speedModifier);
            this.ticksUntilNextAction = 0;
            this.combatTicks = 0;
            this.initialMobHealth = mob.getHealth() / mob.getMaxHealth();
            this.initialTargetHealth = target.getHealth() / target.getMaxHealth();
            selectNextAction();
        }
        
        @Override
        public void stop() {
            // Combat ended - record outcome for learning
            if (this.target != null) {
                recordCombatOutcome();
            }
            
            this.target = null;
            this.mob.getNavigation().stop();
            this.combatTicks = 0;
        }
        
        @Override
        public void tick() {
            if (this.target == null) {
                return;
            }
            
            combatTicks++;
            
            this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            
            // Re-evaluate action periodically
            if (--this.ticksUntilNextAction <= 0) {
                selectNextAction();
                this.ticksUntilNextAction = 20 + mob.getRandom().nextInt(20); // 1-2 seconds
            }
            
            // Execute current action
            executeAction();
        }
        
        /**
         * Record combat outcome for ML learning
         */
        private void recordCombatOutcome() {
            if (behaviorAI == null || target == null) return;
            
            // Build final state
            MobBehaviorAI.MobState finalState = new MobBehaviorAI.MobState(
                mob.getHealth() / mob.getMaxHealth(),
                target.getHealth() / target.getMaxHealth(),
                (float) mob.distanceTo(target)
            );
            finalState.combatTime = combatTicks / 20.0f;  // Convert to seconds
            finalState.isNight = !mob.level().isDay();
            finalState.biome = mob.level().getBiome(mob.blockPosition()).toString();
            
            // Check outcomes
            boolean mobDied = !mob.isAlive();
            boolean playerDied = !target.isAlive();
            
            // Record for learning
            behaviorAI.recordCombatOutcome(mobId, playerDied, mobDied, finalState);
        }
        
        /**
         * Select next action using AI with mob instance tracking
         */
        private void selectNextAction() {
            if (target == null) return;
            
            // Build current state
            MobBehaviorAI.MobState state = new MobBehaviorAI.MobState(
                mob.getHealth() / mob.getMaxHealth(),
                target.getHealth() / target.getMaxHealth(),
                (float) mob.distanceTo(target)
            );
            
            state.isNight = !mob.level().isDay();
            state.biome = mob.level().getBiome(mob.blockPosition()).toString();
            state.combatTime = combatTicks / 20.0f;
            
            // Special abilities
            if (mob instanceof Spider) {
                state.canClimbWalls = true;
            }
            
            // Get mob type
            String mobType = mob.getClass().getSimpleName().toLowerCase();
            
            // AI selects action (pass mob ID for tracking)
            currentAction = behaviorAI.selectMobAction(mobType, state, mobId);
        }
        
        /**
         * Execute the selected action
         */
        private void executeAction() {
            if (target == null) return;
            
            double distance = mob.distanceTo(target);
            double baseSpeed = speedModifier;
            
            switch (currentAction) {
                case "straight_charge":
                    // Direct charge at target
                    mob.getNavigation().moveTo(target, baseSpeed * 1.2);
                    break;
                    
                case "circle_strafe":
                    // Circle around target
                    circleAroundTarget(baseSpeed);
                    break;
                    
                case "kite_backward":
                    // Move away while attacking (for ranged)
                    if (distance < 8.0) {
                        retreatFromTarget(baseSpeed * 1.1);
                    }
                    break;
                    
                case "ambush":
                    // Wait for player to get closer
                    if (distance > 5.0) {
                        mob.getNavigation().stop();
                    } else {
                        mob.getNavigation().moveTo(target, baseSpeed * 1.5);
                    }
                    break;
                    
                case "group_rush":
                case "suicide_rush":
                    // Aggressive charge
                    mob.getNavigation().moveTo(target, baseSpeed * 1.3);
                    break;
                    
                case "retreat_reload":
                case "fake_retreat":
                    // Tactical retreat
                    if (mob.getRandom().nextFloat() < 0.7f) {
                        retreatFromTarget(baseSpeed);
                    }
                    break;
                    
                default:
                    // Default behavior
                    mob.getNavigation().moveTo(target, baseSpeed);
                    break;
            }
            
            // Attack if in range
            if (distance <= mob.getBbWidth() * 2.0F + target.getBbWidth()) {
                mob.doHurtTarget(target);
            }
        }
        
        /**
         * Circle around the target
         */
        private void circleAroundTarget(double speed) {
            if (target == null) return;
            
            double angle = Math.atan2(mob.getZ() - target.getZ(), mob.getX() - target.getX());
            double circleAngle = angle + Math.PI / 4; // 45 degree offset
            
            double distance = 4.0; // Desired distance
            double targetX = target.getX() + Math.cos(circleAngle) * distance;
            double targetZ = target.getZ() + Math.sin(circleAngle) * distance;
            
            mob.getNavigation().moveTo(targetX, target.getY(), targetZ, speed);
        }
        
        /**
         * Retreat from target
         */
        private void retreatFromTarget(double speed) {
            if (target == null) return;
            
            double angle = Math.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX());
            
            double distance = 5.0;
            double targetX = mob.getX() - Math.cos(angle) * distance;
            double targetZ = mob.getZ() - Math.sin(angle) * distance;
            
            mob.getNavigation().moveTo(targetX, mob.getY(), targetZ, speed);
        }
    }
}
