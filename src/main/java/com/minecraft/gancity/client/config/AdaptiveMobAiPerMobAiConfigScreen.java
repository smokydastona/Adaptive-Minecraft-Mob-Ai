package com.minecraft.gancity.client.config;

import com.minecraft.gancity.config.PerMobAiDefaultsStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Client-side config UI for enabling/disabling this mod's AI per entity type.
 *
 * When disabled, the mob keeps its default/vanilla AI (this mod will not inject goals).
 */
@SuppressWarnings({"null", "ConstantConditions", "deprecation"})
public final class AdaptiveMobAiPerMobAiConfigScreen extends Screen {
    private final Screen parent;

    private EditBox search;
    private EntityList entityList;
    private String query = "";

    private Button toggleEnabled;
    private Button setVanilla;
    private Button setEnhanced;
    private Button clearOverride;

    private boolean showVanilla = true;
    private Button toggleShowVanilla;

    private String selectedEntityId;

    // Local editable copy (write on Save)
    private PerMobAiDefaultsStore.Config working;
    private List<String> allEntityIds = List.of();

    public AdaptiveMobAiPerMobAiConfigScreen(Screen parent) {
        super(Component.literal("Adaptive Mob AI - Per Mob AI"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PerMobAiDefaultsStore.loadIfNeeded();
        working = deepCopy(PerMobAiDefaultsStore.get());

        allEntityIds = buildEntityIdList(showVanilla);

        int leftWidth = Math.max(240, this.width / 3);
        int rightX = leftWidth + 14;

        search = new EditBox(this.font, 10, 12, leftWidth - 20, 18, Component.literal("Search entities"));
        search.setMaxLength(128);
        search.setValue(query == null ? "" : query);
        search.setResponder(s -> {
            query = s;
            refreshList();
        });
        addRenderableWidget(search);

        int bottomButtonsY = this.height - 28;
        entityList = new EntityList(this.minecraft, leftWidth, this.height, 36, bottomButtonsY - 12, 18);
        entityList.setLeftPos(10);
        addRenderableWidget(entityList);

        int rowY = 42;
        int gap = 4;
        int rowH = 20;
        int fieldW = Math.min(320, this.width - rightX - 12);

        toggleEnabled = addRenderableWidget(Button.builder(Component.literal("Overrides: " + (working.enabled ? "ON" : "OFF")), b -> {
            working.enabled = !working.enabled;
            refreshRightPanel();
        }).bounds(rightX, rowY, fieldW, rowH).build());

        toggleShowVanilla = addRenderableWidget(Button.builder(Component.literal("Show Vanilla: " + (showVanilla ? "ON" : "OFF")), b -> {
            showVanilla = !showVanilla;
            allEntityIds = buildEntityIdList(showVanilla);
            refreshList();
            toggleShowVanilla.setMessage(Component.literal("Show Vanilla: " + (showVanilla ? "ON" : "OFF")));
        }).bounds(rightX, rowY + (rowH + gap), fieldW, rowH).build());

        setVanilla = addRenderableWidget(Button.builder(Component.literal("Set: Vanilla AI"), b -> {
            if (selectedEntityId != null) {
                working.aiEnabledOverrides.put(selectedEntityId, false);
                refreshRightPanel();
                refreshList();
            }
        }).bounds(rightX, rowY + (rowH + gap) * 2, fieldW, rowH).build());

        setEnhanced = addRenderableWidget(Button.builder(Component.literal("Set: Enhanced AI"), b -> {
            if (selectedEntityId != null) {
                working.aiEnabledOverrides.put(selectedEntityId, true);
                refreshRightPanel();
                refreshList();
            }
        }).bounds(rightX, rowY + (rowH + gap) * 3, fieldW, rowH).build());

        clearOverride = addRenderableWidget(Button.builder(Component.literal("Clear Override"), b -> {
            if (selectedEntityId != null) {
                working.aiEnabledOverrides.remove(selectedEntityId);
                refreshRightPanel();
                refreshList();
            }
        }).bounds(rightX, rowY + (rowH + gap) * 4, fieldW, rowH).build());

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            applyAndSave();
            onClose();
        }).bounds(rightX, bottomButtonsY, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(rightX + 110, bottomButtonsY, 100, 20)
            .build());

        if (!allEntityIds.isEmpty()) {
            setSelectedEntity(allEntityIds.get(0));
        }
        refreshList();
        refreshRightPanel();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void applyAndSave() {
        PerMobAiDefaultsStore.setEnabled(working.enabled);

        PerMobAiDefaultsStore.Config persisted = PerMobAiDefaultsStore.get();
        persisted.aiEnabledOverrides.clear();
        persisted.aiEnabledOverrides.putAll(working.aiEnabledOverrides);
        PerMobAiDefaultsStore.save();
    }

    private void refreshList() {
        if (entityList == null) {
            return;
        }

        String q = search == null ? query : search.getValue();
        String normalized = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<String> filtered;
        if (normalized.isEmpty()) {
            filtered = allEntityIds;
        } else {
            filtered = allEntityIds.stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
        }

        entityList.replaceEntries(filtered);
    }

    private void setSelectedEntity(String entityId) {
        selectedEntityId = entityId;
        refreshRightPanel();
    }

    private void refreshRightPanel() {
        toggleEnabled.setMessage(Component.literal("Overrides: " + (working.enabled ? "ON" : "OFF")));

        boolean hasSelection = selectedEntityId != null;
        setVanilla.active = hasSelection;
        setEnhanced.active = hasSelection;
        clearOverride.active = hasSelection;

        if (!hasSelection) {
            return;
        }

        Boolean override = working.aiEnabledOverrides.get(selectedEntityId);
        String state;
        if (override == null) {
            state = "Default (Enhanced)";
        } else {
            state = override ? "Enhanced" : "Vanilla";
        }
        setVanilla.setMessage(Component.literal("Set: Vanilla AI (current: " + state + ")"));
        setEnhanced.setMessage(Component.literal("Set: Enhanced AI (current: " + state + ")"));
    }

    private static PerMobAiDefaultsStore.Config deepCopy(PerMobAiDefaultsStore.Config cfg) {
        PerMobAiDefaultsStore.Config copy = new PerMobAiDefaultsStore.Config();
        if (cfg == null) {
            return copy;
        }
        copy.enabled = cfg.enabled;
        copy.aiEnabledOverrides = new java.util.HashMap<>();
        if (cfg.aiEnabledOverrides != null) {
            copy.aiEnabledOverrides.putAll(cfg.aiEnabledOverrides);
        }
        return copy;
    }

    private static List<String> buildEntityIdList(boolean includeVanilla) {
        Set<ResourceLocation> keys = BuiltInRegistries.ENTITY_TYPE.keySet();

        return keys.stream()
            .filter(rl -> includeVanilla || !"minecraft".equals(rl.getNamespace()))
            .map(ResourceLocation::toString)
            .sorted()
            .collect(Collectors.toList());
    }

    private final class EntityList extends ObjectSelectionList<EntityEntry> {
        public EntityList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight) {
            super(mc, width, height, y0, y1, itemHeight);
        }

        void replaceEntries(List<String> entityIds) {
            this.clearEntries();
            if (entityIds == null) {
                return;
            }
            for (String id : entityIds) {
                this.addEntry(new EntityEntry(id));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 20;
        }
    }

    private final class EntityEntry extends ObjectSelectionList.Entry<EntityEntry> {
        private final String entityId;

        EntityEntry(String entityId) {
            this.entityId = entityId;
        }

        @Override
        public Component getNarration() {
            return Component.literal(entityId);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            String suffix = buildSuffix(entityId);
            graphics.drawString(font, entityId + suffix, x + 4, y + 2, 0xFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            entityList.setSelected(this);
            setSelectedEntity(entityId);
            return true;
        }

        private String buildSuffix(String entityId) {
            Boolean override = working.aiEnabledOverrides.get(entityId);
            if (override == null) {
                return "";
            }
            return override ? " [enhanced]" : " [vanilla]";
        }
    }
}
