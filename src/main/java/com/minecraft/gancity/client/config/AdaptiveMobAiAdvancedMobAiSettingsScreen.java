package com.minecraft.gancity.client.config;

import com.minecraft.gancity.config.ModdedMobTacticMappingStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Modded-only "Advanced Mob AI Settings" screen.
 * Lists all non-minecraft: mob entity types (non-MISC) and opens per-mob settings.
 */
@SuppressWarnings({"null", "ConstantConditions"})
public final class AdaptiveMobAiAdvancedMobAiSettingsScreen extends Screen {
    private final Screen parent;

    private EditBox search;
    private MobList mobList;
    private String query = "";

    private List<String> allMobIds = List.of();

    private Button toggleAutoAssign;

    public AdaptiveMobAiAdvancedMobAiSettingsScreen(Screen parent) {
        super(Component.literal("Adaptive Mob AI - Advanced Mob AI Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModdedMobTacticMappingStore.loadIfNeeded();
        ModdedMobTacticMappingStore.Config cfg = ModdedMobTacticMappingStore.get();

        allMobIds = buildMobIdList(false);

        int listWidth = Math.max(280, this.width / 2);
        int listX = (this.width - listWidth) / 2;

        int y = 12;

        toggleAutoAssign = addRenderableWidget(Button.builder(Component.literal("Auto-Assign Modded Profiles: " + (cfg.autoAssignEnabled ? "ON" : "OFF")), b -> {
            ModdedMobTacticMappingStore.setAutoAssignEnabled(!ModdedMobTacticMappingStore.get().autoAssignEnabled);
            ModdedMobTacticMappingStore.loadIfNeeded();
            boolean now = ModdedMobTacticMappingStore.get().autoAssignEnabled;
            toggleAutoAssign.setMessage(Component.literal("Auto-Assign Modded Profiles: " + (now ? "ON" : "OFF")));
        }).bounds(listX, y, listWidth, 20).build());

        y += 26;

        search = new EditBox(this.font, listX, y, listWidth, 18, Component.literal("Search mobs"));
        search.setMaxLength(128);
        search.setValue(query == null ? "" : query);
        search.setResponder(s -> {
            query = s;
            refreshList();
        });
        addRenderableWidget(search);

        int bottomButtonsY = this.height - 28;
        mobList = new MobList(this.minecraft, listWidth, this.height, y + 26, bottomButtonsY - 12, 18);
        mobList.setLeftPos(listX);
        addRenderableWidget(mobList);

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(listX, bottomButtonsY, 120, 20)
            .build());

        refreshList();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void refreshList() {
        if (mobList == null) {
            return;
        }

        String q = search == null ? query : search.getValue();
        String normalized = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<String> filtered;
        if (normalized.isEmpty()) {
            filtered = allMobIds;
        } else {
            filtered = allMobIds.stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
        }

        mobList.replaceEntries(filtered);
    }

    private static List<String> buildMobIdList(boolean vanillaOnly) {
        Set<ResourceLocation> keys = BuiltInRegistries.ENTITY_TYPE.keySet();

        return keys.stream()
            .filter(rl -> vanillaOnly == "minecraft".equals(rl.getNamespace()))
            .filter(rl -> {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
                return type.getCategory() != MobCategory.MISC;
            })
            .map(ResourceLocation::toString)
            .sorted()
            .collect(Collectors.toList());
    }

    private final class MobList extends ObjectSelectionList<MobEntry> {
        MobList(net.minecraft.client.Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
            super(minecraft, width, height, y0, y1, itemHeight);
            setRenderBackground(true);
        }

        void replaceEntries(List<String> mobIds) {
            clearEntries();
            if (mobIds == null) {
                return;
            }
            for (String id : mobIds) {
                addEntry(new MobEntry(id));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }
    }

    private final class MobEntry extends ObjectSelectionList.Entry<MobEntry> {
        private final String mobId;

        MobEntry(String mobId) {
            this.mobId = mobId;
        }

        @Override
        public Component getNarration() {
            return Component.literal(mobId);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, mobId, x + 4, y + 4, 0xFFE0E0E0, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            Minecraft.getInstance().setScreen(new AdaptiveMobAiSingleMobSettingsScreen(AdaptiveMobAiAdvancedMobAiSettingsScreen.this, mobId));
            return true;
        }
    }
}
