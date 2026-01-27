package com.minecraft.gancity.client.config;

import com.minecraft.gancity.GANCityMod;
import com.minecraft.gancity.ai.MobBehaviorAI;
import com.minecraft.gancity.config.ModdedMobTacticMappingStore;
import com.minecraft.gancity.config.PerMobAiDefaultsStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Per-mob submenu.
 *
 * Keeps the UI simple:
 * - Toggle Enhanced vs Vanilla AI (goal injection)
 * - Choose tactic profile (override) or clear override to use default/auto
 * - Jump into the existing Loadouts screen
 */
@SuppressWarnings({"null", "ConstantConditions"})
public final class AdaptiveMobAiSingleMobSettingsScreen extends Screen {
    private final Screen parent;
    private final String entityTypeId;

    private Button toggleEnhanced;
    private Button setProfile;
    private Button clearProfile;

    public AdaptiveMobAiSingleMobSettingsScreen(Screen parent, String entityTypeId) {
        super(Component.literal("Mob Settings"));
        this.parent = parent;
        this.entityTypeId = entityTypeId;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 4;
        int w = Math.min(320, this.width - 40);

        addRenderableWidget(Button.builder(Component.literal("Loadouts"), b ->
            Minecraft.getInstance().setScreen(new AdaptiveMobAiLoadoutConfigScreen(this))
        ).bounds(centerX - w / 2, y, w, 20).build());

        toggleEnhanced = addRenderableWidget(Button.builder(Component.literal("Enhanced AI: ?"), b -> {
            boolean currentlyEnabled = PerMobAiDefaultsStore.isAiEnabledFor(entityTypeId);
            // If enabled currently -> set Vanilla (false). If disabled -> set Enhanced (true).
            PerMobAiDefaultsStore.setOverride(entityTypeId, !currentlyEnabled);
            refreshButtons();
        }).bounds(centerX - w / 2, y + 24, w, 20).build());

        setProfile = addRenderableWidget(Button.builder(Component.literal("Tactic Profile: ?"), b -> openProfilePicker())
            .bounds(centerX - w / 2, y + 48, w, 20)
            .build());

        clearProfile = addRenderableWidget(Button.builder(Component.literal("Clear Profile Override"), b -> {
            ModdedMobTacticMappingStore.setOverride(entityTypeId, null);
            refreshButtons();
        }).bounds(centerX - w / 2, y + 72, w, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(centerX - w / 2, y + 108, w, 20)
            .build());

        refreshButtons();
    }

    private void refreshButtons() {
        boolean enhanced = PerMobAiDefaultsStore.isAiEnabledFor(entityTypeId);
        toggleEnhanced.setMessage(Component.literal("Enhanced AI: " + (enhanced ? "ON" : "OFF (Vanilla)")));

        String currentOverride = ModdedMobTacticMappingStore.getOverride(entityTypeId).orElse(null);
        String computed = computeEffectiveProfile(entityTypeId, currentOverride);

        String label;
        if (currentOverride != null && !currentOverride.isBlank()) {
            label = "override: " + currentOverride;
        } else {
            label = computed == null ? "(unknown)" : ("default/auto: " + computed);
        }
        setProfile.setMessage(Component.literal("Tactic Profile: " + label));
    }

    private void openProfilePicker() {
        List<String> options = new ArrayList<>();
        options.add("(default)");
        options.addAll(loadProfileKeyOptions());

        String current = ModdedMobTacticMappingStore.getOverride(entityTypeId).orElse("(default)");
        if (current == null || current.isBlank()) {
            current = "(default)";
        }

        Minecraft.getInstance().setScreen(new StringSelectScreen(
            this,
            Component.literal("Select Tactic Profile"),
            options,
            current,
            selected -> {
                if (selected == null || selected.equalsIgnoreCase("(default)")) {
                    ModdedMobTacticMappingStore.setOverride(entityTypeId, null);
                } else {
                    ModdedMobTacticMappingStore.setOverride(entityTypeId, selected);
                }
                refreshButtons();
            }
        ));
    }

    private static List<String> loadProfileKeyOptions() {
        MobBehaviorAI ai = GANCityMod.getMobBehaviorAI();
        if (ai != null) {
            return ai.getAvailableProfileKeys();
        }

        // Fallback: vanilla mob entity paths as profile keys
        return BuiltInRegistries.ENTITY_TYPE.keySet().stream()
            .filter(rl -> "minecraft".equals(rl.getNamespace()))
            .filter(rl -> {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
                return type.getCategory() != MobCategory.MISC;
            })
            .map(ResourceLocation::getPath)
            .sorted()
            .toList();
    }

    private static String computeEffectiveProfile(String entityTypeId, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim().toLowerCase(Locale.ROOT);
        }

        ResourceLocation rl = ResourceLocation.tryParse(entityTypeId == null ? "" : entityTypeId);
        if (rl == null) {
            return null;
        }

        if ("minecraft".equals(rl.getNamespace())) {
            return rl.getPath();
        }

        // For modded mobs, fall back to the same heuristic used elsewhere: path similarity by category.
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
        MobCategory category = type.getCategory();

        // If auto-assign is off, show fallback default
        ModdedMobTacticMappingStore.Config cfg = ModdedMobTacticMappingStore.get();
        if (cfg == null) {
            return category == MobCategory.MONSTER ? "zombie" : "cow";
        }
        if (!cfg.enabled) {
            return category == MobCategory.MONSTER ? cfg.defaultHostileProfile : cfg.defaultPassiveProfile;
        }

        // Namespace default
        String nsDefault = ModdedMobTacticMappingStore.getNamespaceDefault(rl.getNamespace()).orElse(null);
        if (nsDefault != null && !nsDefault.isBlank()) {
            return nsDefault;
        }

        if (!cfg.autoAssignEnabled) {
            return category == MobCategory.MONSTER ? cfg.defaultHostileProfile : cfg.defaultPassiveProfile;
        }

        // Very small heuristic: if the modded path contains a vanilla mob path substring, use it.
        String path = rl.getPath().toLowerCase(Locale.ROOT);
        for (ResourceLocation v : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (!"minecraft".equals(v.getNamespace())) {
                continue;
            }
            EntityType<?> vt = BuiltInRegistries.ENTITY_TYPE.get(v);
            if (vt.getCategory() != category) {
                continue;
            }
            String vp = v.getPath().toLowerCase(Locale.ROOT);
            if (path.contains(vp) || vp.contains(path)) {
                return vp;
            }
        }

        return category == MobCategory.MONSTER ? cfg.defaultHostileProfile : cfg.defaultPassiveProfile;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
