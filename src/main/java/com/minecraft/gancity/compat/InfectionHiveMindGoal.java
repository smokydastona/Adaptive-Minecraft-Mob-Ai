package com.minecraft.gancity.compat;

import com.minecraft.gancity.GANCityMod;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Lightweight "hive mind" support for infection-style mods.
 *
 * This goal is intentionally non-invasive: it does not move/pathfind or attack;
 * it only shares targets to nearby infected mobs.
 */
public final class InfectionHiveMindGoal extends Goal {
    private final Mob mob;

    public InfectionHiveMindGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (mob == null || !mob.isAlive()) {
            return false;
        }
        if (mob.level().isClientSide()) {
            return false;
        }
        if (!GANCityMod.isInfectionHiveMindEnabled()) {
            return false;
        }
        if (!GANCityMod.isInfectionHiveMindMob(mob.getType())) {
            return false;
        }
        var target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void tick() {
        GANCityMod.tryInfectionHiveMindBroadcast(mob);
    }
}
