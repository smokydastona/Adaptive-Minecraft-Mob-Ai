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

        addRenderableWidget(Button.builder(Component.literal("Mob Loadouts"), b ->
            Minecraft.getInstance().setScreen(new AdaptiveMobAiLoadoutConfigScreen(this))
        ).bounds(centerX - w / 2, y, w, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Modded Mob Tactics"), b ->
            Minecraft.getInstance().setScreen(new AdaptiveMobAiModdedMobTacticsConfigScreen(this))
        ).bounds(centerX - w / 2, y + 24, w, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Per Mob AI (Vanilla/Enhanced)"), b ->
            Minecraft.getInstance().setScreen(new AdaptiveMobAiPerMobAiConfigScreen(this))
        ).bounds(centerX - w / 2, y + 48, w, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(centerX - w / 2, y + 84, w, 20)
            .build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
