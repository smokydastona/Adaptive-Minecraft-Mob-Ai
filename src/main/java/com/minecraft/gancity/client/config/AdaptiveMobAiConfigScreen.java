package com.minecraft.gancity.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Root config screen for Adaptive Mob AI.
 *
 * Forge only has a single "Config" entry-point in the Mods UI; we use this
 * screen as a hub to reach individual config pages.
 */
@SuppressWarnings("null")
public final class AdaptiveMobAiConfigScreen extends Screen {
    private final Screen parent;

    public AdaptiveMobAiConfigScreen(Screen parent) {
        super(Component.literal("Adaptive Mob AI - Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 4;
        int w = Math.min(260, this.width - 40);

        Button mobAiSettings = addRenderableWidget(Button.builder(Component.literal("Mob AI Settings"), b ->
            Minecraft.getInstance().setScreen(new AdaptiveMobAiMobAiSettingsScreen(this))
        ).bounds(centerX - w / 2, y, w, 20).build());
        AdaptiveMobAiUiText.setTooltip(mobAiSettings, "config.adaptivemobai.tooltip.root.mob_ai_settings");

        Button advancedMobAiSettings = addRenderableWidget(Button.builder(Component.literal("Advanced Mob AI Settings"), b ->
            Minecraft.getInstance().setScreen(new AdaptiveMobAiAdvancedMobAiSettingsScreen(this))
        ).bounds(centerX - w / 2, y + 24, w, 20).build());
        AdaptiveMobAiUiText.setTooltip(advancedMobAiSettings, "config.adaptivemobai.tooltip.root.advanced_mob_ai_settings");

        Button disableLoadouts = addRenderableWidget(Button.builder(Component.literal("Disable Loadouts Globally"), b ->
            AdaptiveMobAiLoadoutConfigScreen.disableLoadoutsGlobally()
        ).bounds(centerX - w / 2, y + 48, w, 20).build());
        AdaptiveMobAiUiText.setTooltip(disableLoadouts, "config.adaptivemobai.tooltip.root.disable_loadouts_globally");

        Button done = addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(centerX - w / 2, y + 84, w, 20)
            .build());
        AdaptiveMobAiUiText.setTooltip(done, "config.adaptivemobai.tooltip.common.done");
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
