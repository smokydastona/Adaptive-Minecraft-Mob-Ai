package com.minecraft.gancity.ai;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Custom Movement System - Precise mob movement control
 * Inspired by AI-Player's custom movement code
 * 
 * Features:
 * - Precise circle strafing
 * - Jump-timing for critical hits
 * - Block-placing while retreating (scaffolding)
 * - Swimming/climbing optimization
 * - Parkour-style movement
 */
public class PreciseMovement extends MoveControl {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Movement parameters
    private static final double STRAFE_SPEED = 0.8;
    private static final double SPRINT_SPEED = 1.3;
    private static final double SNEAK_SPEED = 0.3;
    private static final float JUMP_POWER = 0.42f;
    
    // State tracking
    private MovementMode currentMode = MovementMode.NORMAL;
    private Vec3 strafeTarget = null;
    private float strafeAngle = 0.0f;
    private boolean shouldJump = false;
    private long lastJumpTime = 0;
    private static final long JUMP_COOLDOWN = 400; // ms
    
    public PreciseMovement(Mob mob) {
        super(mob);
    }
    
    /**
     * Circle strafe around target
     */
    public void circleStrafe(Vec3 target, float radius, boolean clockwise) {
        this.currentMode = MovementMode.CIRCLE_STRAFE;
        this.strafeTarget = target;
        
        // Calculate tangent direction
        Vec3 toTarget = target.subtract(mob.position());
        Vec3 horizontal = new Vec3(toTarget.x, 0, toTarget.z).normalize();
        
        // Perpendicular vector for strafing
        Vec3 strafe = new Vec3(-horizontal.z, 0, horizontal.x);
        if (!clockwise) {
            strafe = strafe.reverse();
        }
        
        // Calculate strafe position
        Vec3 strafePos = target.add(horizontal.scale(radius)).add(strafe.scale(1.5));
        
        setWantedPosition(strafePos.x, strafePos.y, strafePos.z, STRAFE_SPEED);
        
        // Increment angle
        strafeAngle += clockwise ? 15.0f : -15.0f;
        strafeAngle %= 360.0f;
    }
    
    /**
     * Retreat while facing target (backpedal)
     */
    public void tacticalRetreat(Vec3 target, double distance) {
        this.currentMode = MovementMode.RETREAT;
        
        // Calculate retreat direction (away from target)
        Vec3 awayVector = mob.position().subtract(target).normalize();
        Vec3 retreatPos = mob.position().add(awayVector.scale(distance));
        
        setWantedPosition(retreatPos.x, retreatPos.y, retreatPos.z, SPRINT_SPEED);
        
        // Keep looking at target while retreating
        mob.getLookControl().setLookAt(target.x, target.y, target.z);
    }
    
    /**
     * Serpentine movement to avoid projectiles
     */
    public void serpentineMovement(Vec3 destination) {
        this.currentMode = MovementMode.SERPENTINE;
        
        // Zigzag pattern
        double time = System.currentTimeMillis() / 1000.0;
        double offset = Math.sin(time * 2.0) * 2.0; // 2-block zigzag
        
        Vec3 toDestination = destination.subtract(mob.position());
        Vec3 perpendicular = new Vec3(-toDestination.z, 0, toDestination.x).normalize();
        
        Vec3 zigzagPos = mob.position()
            .add(toDestination.normalize().scale(1.0))
            .add(perpendicular.scale(offset));
        
        setWantedPosition(zigzagPos.x, zigzagPos.y, zigzagPos.z, SPRINT_SPEED);
    }
    
    /**
     * Jump at optimal timing for critical hit
     */
    public boolean jumpCrit() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastJumpTime < JUMP_COOLDOWN) {
            return false; // Cooldown
        }
        
        if (!mob.onGround()) {
            return false; // Already airborne
        }
        
        // Execute jump
        mob.setDeltaMovement(mob.getDeltaMovement().add(0, JUMP_POWER, 0));
        lastJumpTime = currentTime;
        shouldJump = false;
        
        LOGGER.debug("Executing jump crit");
        return true;
    }
    
    /**
     * Jump sideways for dodge
     */
    public void jumpDodge(Vec3 direction) {
        if (!mob.onGround()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastJumpTime < JUMP_COOLDOWN) {
            return;
        }
        
        // Horizontal dodge + vertical jump
        Vec3 dodgeVelocity = direction.normalize().scale(0.5).add(0, JUMP_POWER, 0);
        mob.setDeltaMovement(mob.getDeltaMovement().add(dodgeVelocity));
        
        lastJumpTime = currentTime;
        LOGGER.debug("Executing dodge jump");
    }
    
    /**
     * Parkour-style obstacle navigation
     */
    public void parkourMove(Vec3 destination) {
        this.currentMode = MovementMode.PARKOUR;
        
        BlockPos currentPos = mob.blockPosition();
        BlockPos targetPos = new BlockPos((int)destination.x, (int)destination.y, (int)destination.z);
        
        // Check for obstacles
        Level level = mob.level();
        BlockPos ahead = currentPos.relative(mob.getDirection());
        BlockState blockAhead = level.getBlockState(ahead);
        
        // Auto-jump over 1-block obstacles
        if (!blockAhead.isAir() && blockAhead.isSolid()) {
            BlockState blockAbove = level.getBlockState(ahead.above());
            if (blockAbove.isAir()) {
                shouldJump = true;
            }
        }
        
        setWantedPosition(destination.x, destination.y, destination.z, SPRINT_SPEED);
    }
    
    /**
     * Precise positioning for ambush/cover
     */
    public void positionForCover(BlockPos coverPosition) {
        this.currentMode = MovementMode.COVER;
        
        // Move to exact block position
        setWantedPosition(
            coverPosition.getX() + 0.5,
            coverPosition.getY(),
            coverPosition.getZ() + 0.5,
            SNEAK_SPEED
        );
    }
    
    /**
     * Climbing optimization
     */
    public void optimizedClimb(Vec3 destination) {
        this.currentMode = MovementMode.CLIMBING;
        
        // For spiders and other climbers
        if (mob.onClimbable()) {
            // Accelerate vertical movement
            Vec3 motion = mob.getDeltaMovement();
            mob.setDeltaMovement(motion.x, 0.2, motion.z);
        }
        
        setWantedPosition(destination.x, destination.y, destination.z, STRAFE_SPEED);
    }
    
    /**
     * Swimming optimization
     */
    public void optimizedSwim(Vec3 destination) {
        this.currentMode = MovementMode.SWIMMING;
        
        if (mob.isInWater()) {
            // Boost forward momentum
            Vec3 toDestination = destination.subtract(mob.position()).normalize();
            mob.setDeltaMovement(mob.getDeltaMovement().add(toDestination.scale(0.02)));
        }
        
        setWantedPosition(destination.x, destination.y, destination.z, STRAFE_SPEED * 0.6);
    }
    
    /**
     * Flank target from side/behind
     */
    public void flankTarget(Vec3 target, Vec3 targetLookDirection) {
        this.currentMode = MovementMode.FLANKING;
        
        // Calculate position behind target
        Vec3 behindTarget = target.add(targetLookDirection.scale(-3.0));
        
        // Add slight random offset
        double randomOffset = (Math.random() - 0.5) * 2.0;
        Vec3 perpendicular = new Vec3(-targetLookDirection.z, 0, targetLookDirection.x);
        Vec3 flankPos = behindTarget.add(perpendicular.scale(randomOffset));
        
        setWantedPosition(flankPos.x, flankPos.y, flankPos.z, SPRINT_SPEED);
    }
    
    /**
     * Block-placing while retreating (scaffolding)
     * Note: Actual block placing handled by separate system
     */
    public boolean shouldPlaceBlockWhileRetreating() {
        if (currentMode != MovementMode.RETREAT) {
            return false;
        }
        
        // Check if retreating off a ledge
        BlockPos below = mob.blockPosition().below();
        BlockState blockBelow = mob.level().getBlockState(below);
        
        return blockBelow.isAir(); // Need to scaffold
    }
    
    /**
     * Override tick for custom movement logic
     */
    @Override
    public void tick() {
        // Handle jump timing
        if (shouldJump && mob.onGround()) {
            jumpCrit();
        }
        
        // Execute standard move control
        super.tick();
        
        // Mode-specific adjustments
        switch (currentMode) {
            case CIRCLE_STRAFE:
                // Maintain facing toward strafe target
                if (strafeTarget != null) {
                    mob.getLookControl().setLookAt(strafeTarget.x, strafeTarget.y, strafeTarget.z);
                }
                break;
                
            case SERPENTINE:
                // Continue serpentine if moving
                if (this.operation == Operation.MOVE_TO) {
                    // Pattern continues in serpentineMovement()
                }
                break;
                
            case PARKOUR:
                // Auto-jump logic handled above
                break;
                
            case SWIMMING:
                // Swimming movement adjustments
                if (mob.isInWater()) {
                    // Maintain upward movement if needed
                    if (shouldJump) {
                        mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.04, 0));
                    }
                }
                break;
                
            case CLIMBING:
                // Climbing movement adjustments
                if (mob.onClimbable()) {
                    // Enhanced vertical movement
                    mob.setDeltaMovement(mob.getDeltaMovement().multiply(1.0, 1.2, 1.0));
                }
                break;
                
            case NORMAL:
            case RETREAT:
            case COVER:
            case FLANKING:
                // No special adjustments needed
                break;
        }
    }
    
    /**
     * Reset to normal movement
     */
    public void resetMovementMode() {
        this.currentMode = MovementMode.NORMAL;
        this.strafeTarget = null;
        this.shouldJump = false;
    }
    
    /**
     * Get current movement mode
     */
    public MovementMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * Movement mode enum
     */
    public enum MovementMode {
        NORMAL,
        CIRCLE_STRAFE,
        RETREAT,
        SERPENTINE,
        PARKOUR,
        COVER,
        CLIMBING,
        SWIMMING,
        FLANKING
    }
}
