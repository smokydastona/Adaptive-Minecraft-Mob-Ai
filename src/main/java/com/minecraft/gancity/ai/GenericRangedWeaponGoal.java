package com.minecraft.gancity.ai;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Objects;

/**
 * A standalone ranged-weapon goal (Forge-side) that enables non-ranged mobs (e.g., zombies)
 * to use bows/crossbows/tridents as actual ranged weapons.
 *
 * This is intentionally not tied to Mixins or ML initialization so it can run in reduced-feature
 * mode and in environments where mixin goal injection may be unreliable.
 */
public final class GenericRangedWeaponGoal extends Goal {
    private final Mob mob;
    private final double speedModifier;

    private LivingEntity target;
    private int attackCooldownTicks = 0;

    private static final int MIN_COOLDOWN_TICKS = 25;
    private static final int MAX_COOLDOWN_TICKS = 45;

    private static final double PREFERRED_MIN_DISTANCE_SQR = 36.0;  // 6 blocks
    private static final double PREFERRED_MAX_DISTANCE_SQR = 144.0; // 12 blocks

    public GenericRangedWeaponGoal(Mob mob, double speedModifier) {
        this.mob = Objects.requireNonNull(mob, "mob");
        this.speedModifier = speedModifier;
        EnumSet<Goal.Flag> goalFlags = EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK);
        this.setFlags(Objects.requireNonNull(goalFlags, "goalFlags"));
    }

    private boolean isHoldingSupportedRangedWeapon() {
        ItemStack main = mob.getMainHandItem();
        Object mainItem = Objects.requireNonNull(main.getItem(), "mainHandItem");
        return mainItem instanceof BowItem
            || mainItem instanceof CrossbowItem
            || main.is(Objects.requireNonNull(Items.BOW, "bow"))
            || main.is(Objects.requireNonNull(Items.CROSSBOW, "crossbow"))
            || main.is(Objects.requireNonNull(Items.TRIDENT, "trident"));
    }

    private boolean isHoldingCrossbow() {
        ItemStack main = mob.getMainHandItem();
        return Objects.requireNonNull(main.getItem(), "mainHandItem") instanceof CrossbowItem
            || main.is(Objects.requireNonNull(Items.CROSSBOW, "crossbow"));
    }

    private boolean isHoldingTrident() {
        return mob.getMainHandItem().is(Objects.requireNonNull(Items.TRIDENT, "trident"));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive()) return false;
        if (!isHoldingSupportedRangedWeapon()) return false;
        this.target = t;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive()) return false;
        return isHoldingSupportedRangedWeapon();
    }

    @Override
    public void start() {
        attackCooldownTicks = 0;
    }

    @Override
    public void stop() {
        this.target = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) return;
        LivingEntity currentTarget = Objects.requireNonNull(target, "target");
        Mob currentMob = Objects.requireNonNull(mob, "mob");

        currentMob.getLookControl().setLookAt(currentTarget, 30.0F, 30.0F);

        double distSqr = currentMob.distanceToSqr(currentTarget);
        if (distSqr < PREFERRED_MIN_DISTANCE_SQR) {
            Vec3 away = Objects.requireNonNull(currentMob.position(), "mobPosition")
                .subtract(Objects.requireNonNull(currentTarget.position(), "targetPosition"));
            if (away.lengthSqr() < 1.0E-4) {
                away = new Vec3(1, 0, 0);
            }
            Vec3 retreatOffset = Objects.requireNonNull(away.normalize(), "awayNormal").scale(6.0);
            Vec3 retreatPos = Objects.requireNonNull(currentMob.position(), "mobPosition")
                .add(Objects.requireNonNull(retreatOffset, "retreatOffset"));
            currentMob.getNavigation().moveTo(retreatPos.x, retreatPos.y, retreatPos.z, speedModifier);
        } else if (distSqr > PREFERRED_MAX_DISTANCE_SQR) {
            currentMob.getNavigation().moveTo(currentTarget, speedModifier);
        } else {
            currentMob.getNavigation().stop();
        }

        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
            return;
        }

        if (!currentMob.getSensing().hasLineOfSight(currentTarget)) {
            return;
        }

        Level level = Objects.requireNonNull(currentMob.level(), "level");
        if (level.isClientSide) {
            return;
        }

        double dx = currentTarget.getX() - currentMob.getX();
        double dy = currentTarget.getEyeY() - (currentMob.getEyeY() + 0.1);
        double dz = currentTarget.getZ() - currentMob.getZ();

        if (isHoldingTrident()) {
            ThrownTrident trident = new ThrownTrident(level, currentMob, Objects.requireNonNull(currentMob.getMainHandItem().copy(), "tridentStack"));
            trident.setPos(currentMob.getX(), currentMob.getEyeY() - 0.1, currentMob.getZ());
            trident.shoot(dx, dy, dz, 1.6F, 14 - level.getDifficulty().getId() * 4);
            level.addFreshEntity(trident);
            level.playSound(null, currentMob.getX(), currentMob.getY(), currentMob.getZ(), Objects.requireNonNull(SoundEvents.DROWNED_SHOOT, "drownedShoot"), SoundSource.HOSTILE, 1.0F, 1.0F);
        } else {
            Arrow arrow = new Arrow(level, currentMob);
            arrow.setPos(currentMob.getX(), currentMob.getEyeY() - 0.1, currentMob.getZ());
            arrow.setBaseDamage(isHoldingCrossbow() ? 3.0D : 2.0D);
            arrow.shoot(dx, dy, dz, isHoldingCrossbow() ? 1.9F : 1.6F, 14 - level.getDifficulty().getId() * 4);
            level.addFreshEntity(arrow);

            level.playSound(null, currentMob.getX(), currentMob.getY(), currentMob.getZ(),
                isHoldingCrossbow() ? Objects.requireNonNull(SoundEvents.CROSSBOW_SHOOT, "crossbowShoot") : Objects.requireNonNull(SoundEvents.SKELETON_SHOOT, "skeletonShoot"),
                SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        attackCooldownTicks = MIN_COOLDOWN_TICKS + currentMob.getRandom().nextInt(MAX_COOLDOWN_TICKS - MIN_COOLDOWN_TICKS + 1);
    }
}
