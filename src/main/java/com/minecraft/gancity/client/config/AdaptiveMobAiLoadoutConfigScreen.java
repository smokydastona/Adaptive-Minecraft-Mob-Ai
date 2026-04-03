package com.minecraft.gancity.client.config;

import com.minecraft.gancity.GANCityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Client-side config UI for global per-mob weapon loadouts.
 *
 * Uses simple searchable selector screens so players can pick mob + items without typing ids.
 */
@SuppressWarnings("null")
public final class AdaptiveMobAiLoadoutConfigScreen extends Screen {
    private static final String CONFIG_FILE_NAME = "adaptivemobai-common.toml";

    private final Screen parent;

    private EditBox mobSearch;
    private MobList mobList;

    private Button weapon1;
    private Button weapon2;
    private Button weapon3;
    private Button weapon4;
    private Button weapon5;

    private Button globalDefaultArrow;
    private Button mobArrowOverride;

    private List<String> allMobIds = List.of();
    private List<String> allWeaponItemIds = List.of();
    private List<String> allArrowItemIds = List.of();

    private String selectedMobId;

    private boolean loadedFromDisk;
    private String mobSearchQuery = "";

    private final Map<String, MobLoadout> loadouts = new HashMap<>();
    private boolean globalLoadoutsDisabled;
    private String defaultArrowId = "minecraft:arrow";

    public AdaptiveMobAiLoadoutConfigScreen(Screen parent) {
        super(Component.literal("Adaptive Mob AI - Loadouts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!loadedFromDisk) {
            loadFromConfigFile();
            loadedFromDisk = true;
        }

        allMobIds = buildMobIdList(loadouts.keySet());
        allWeaponItemIds = buildWeaponItemIdList();
        allArrowItemIds = buildArrowItemIdList();

        int leftWidth = Math.max(220, this.width / 3);
        int rightX = leftWidth + 14;

        mobSearch = new EditBox(this.font, 10, 12, leftWidth - 20, 18, Component.literal("Search mobs"));
        mobSearch.setMaxLength(128);
        mobSearch.setValue(mobSearchQuery == null ? "" : mobSearchQuery);
        mobSearch.setResponder(s -> {
            mobSearchQuery = s;
            refreshMobList();
        });
        addRenderableWidget(mobSearch);

        int bottomButtonsY = this.height - 28;
        mobList = new MobList(this.minecraft, leftWidth, this.height, 36, bottomButtonsY - 12, 18);
        mobList.setLeftPos(10);
        addRenderableWidget(mobList);

        int rowY = 42;
        int rows = 7; // 5 weapon slots + 2 arrow settings
        int gap = 4;
        int maxContentHeight = (bottomButtonsY - 12) - rowY;
        int rowH = Math.max(14, Math.min(22, (maxContentHeight - (rows - 1) * gap) / rows));
        int fieldW = Math.min(300, this.width - rightX - 12);

        int step = rowH + gap;
        weapon1 = addRenderableWidget(Button.builder(Component.literal("Weapon 1: (learned)"), b -> pickWeapon(0)).bounds(rightX, rowY, fieldW, rowH).build());
        weapon2 = addRenderableWidget(Button.builder(Component.literal("Weapon 2: (learned)"), b -> pickWeapon(1)).bounds(rightX, rowY + step, fieldW, rowH).build());
        weapon3 = addRenderableWidget(Button.builder(Component.literal("Weapon 3: (learned)"), b -> pickWeapon(2)).bounds(rightX, rowY + step * 2, fieldW, rowH).build());
        weapon4 = addRenderableWidget(Button.builder(Component.literal("Weapon 4: (learned)"), b -> pickWeapon(3)).bounds(rightX, rowY + step * 3, fieldW, rowH).build());
        weapon5 = addRenderableWidget(Button.builder(Component.literal("Weapon 5: (learned)"), b -> pickWeapon(4)).bounds(rightX, rowY + step * 4, fieldW, rowH).build());
        AdaptiveMobAiUiText.setTooltip(weapon1, "config.adaptivemobai.tooltip.loadouts.weapon_slot", 1);
        AdaptiveMobAiUiText.setTooltip(weapon2, "config.adaptivemobai.tooltip.loadouts.weapon_slot", 2);
        AdaptiveMobAiUiText.setTooltip(weapon3, "config.adaptivemobai.tooltip.loadouts.weapon_slot", 3);
        AdaptiveMobAiUiText.setTooltip(weapon4, "config.adaptivemobai.tooltip.loadouts.weapon_slot", 4);
        AdaptiveMobAiUiText.setTooltip(weapon5, "config.adaptivemobai.tooltip.loadouts.weapon_slot", 5);

        globalDefaultArrow = addRenderableWidget(Button.builder(Component.literal("Default Arrow: minecraft:arrow"), b -> pickGlobalDefaultArrow()).bounds(rightX, rowY + step * 5, fieldW, rowH).build());
        mobArrowOverride = addRenderableWidget(Button.builder(Component.literal("Mob Arrow Override: (default)"), b -> pickMobArrowOverride()).bounds(rightX, rowY + step * 6, fieldW, rowH).build());
        AdaptiveMobAiUiText.setTooltip(globalDefaultArrow, "config.adaptivemobai.tooltip.loadouts.default_arrow");
        AdaptiveMobAiUiText.setTooltip(mobArrowOverride, "config.adaptivemobai.tooltip.loadouts.mob_arrow_override");

        Button save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
            .bounds(rightX, bottomButtonsY, 100, 20)
            .build());
        AdaptiveMobAiUiText.setTooltip(save, "config.adaptivemobai.tooltip.loadouts.save");

        Button cancel = addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(rightX + 110, bottomButtonsY, 100, 20)
            .build());
        AdaptiveMobAiUiText.setTooltip(cancel, "config.adaptivemobai.tooltip.loadouts.cancel");

        // Preserve selection when returning from selector screens
        if (selectedMobId != null && allMobIds.contains(selectedMobId)) {
            setSelectedMob(selectedMobId);
        } else if (!allMobIds.isEmpty()) {
            setSelectedMob(allMobIds.get(0));
        } else {
            setSelectedMob(null);
        }

        refreshMobList();

        refreshRightPanel();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void refreshMobList() {
        if (mobList == null) {
            return;
        }

        String q = mobSearch == null ? mobSearchQuery : mobSearch.getValue();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<String> filtered;
        if (query.isEmpty()) {
            filtered = allMobIds;
        } else {
            filtered = allMobIds.stream().filter(id -> id.toLowerCase(Locale.ROOT).contains(query)).toList();
        }

        mobList.replaceEntries(filtered);
    }

    private void setSelectedMob(String mobId) {
        selectedMobId = mobId;
        refreshRightPanel();
    }

    private void refreshRightPanel() {
        MobLoadout mobLoadout = selectedMobId == null ? null : loadouts.computeIfAbsent(selectedMobId, k -> MobLoadout.empty());

        List<String> weapons = mobLoadout == null ? List.of("", "", "", "", "") : mobLoadout.weapons;
        setWeaponButtonText(weapon1, 1, weapons.get(0));
        setWeaponButtonText(weapon2, 2, weapons.get(1));
        setWeaponButtonText(weapon3, 3, weapons.get(2));
        setWeaponButtonText(weapon4, 4, weapons.get(3));
        setWeaponButtonText(weapon5, 5, weapons.get(4));

        globalDefaultArrow.setMessage(Component.literal("Default Arrow: " + (defaultArrowId == null || defaultArrowId.isBlank() ? "minecraft:arrow" : defaultArrowId)));

        String mobArrow = mobLoadout == null ? "" : mobLoadout.arrowOverride;
        String mobArrowLabel = (mobArrow == null || mobArrow.isBlank()) ? "(default)" : mobArrow;
        mobArrowOverride.setMessage(Component.literal("Mob Arrow Override: " + mobArrowLabel));

        boolean enabled = selectedMobId != null;
        weapon1.active = enabled;
        weapon2.active = enabled;
        weapon3.active = enabled;
        weapon4.active = enabled;
        weapon5.active = enabled;
        mobArrowOverride.active = enabled;
    }

    private static void setWeaponButtonText(Button button, int idx, String value) {
        if (button == null) {
            return;
        }
        button.setMessage(Component.literal("Weapon " + idx + ": ").append(AdaptiveMobAiUiText.loadoutOptionLabel(value)));
    }

    private void pickWeapon(int slot) {
        if (selectedMobId == null) {
            return;
        }

        MobLoadout mobLoadout = loadouts.computeIfAbsent(selectedMobId, k -> MobLoadout.empty());
        List<String> options = new ArrayList<>();
        options.add("(learned)");
        options.add("default");
        options.add("none");
        options.addAll(allWeaponItemIds);

        String current = mobLoadout.weapons.get(slot);
        String currentDisplay = (current == null || current.isBlank()) ? "(learned)" : current;

        Minecraft.getInstance().setScreen(new StringSelectScreen(
            this,
            Component.literal("Select Weapon (slot " + (slot + 1) + ")"),
            options,
            currentDisplay,
            AdaptiveMobAiUiText::loadoutOptionLabel,
            selected -> {
                String normalized = normalizeSelection(selected);
                mobLoadout.weapons.set(slot, normalized);
                refreshRightPanel();
            }
        ));
    }

    private void pickGlobalDefaultArrow() {
        List<String> options = new ArrayList<>();
        options.addAll(allArrowItemIds);

        String current = defaultArrowId == null || defaultArrowId.isBlank() ? "minecraft:arrow" : defaultArrowId;

        Minecraft.getInstance().setScreen(new StringSelectScreen(
            this,
            Component.literal("Select Default Arrow"),
            options,
            current,
            selected -> {
                String normalized = normalizeSelection(selected);
                if (normalized == null || normalized.isBlank() || normalized.equalsIgnoreCase("(empty)") || normalized.equalsIgnoreCase("none")) {
                    defaultArrowId = "minecraft:arrow";
                } else {
                    defaultArrowId = normalized;
                }
                refreshRightPanel();
            }
        ));
    }

    private void pickMobArrowOverride() {
        if (selectedMobId == null) {
            return;
        }

        MobLoadout mobLoadout = loadouts.computeIfAbsent(selectedMobId, k -> MobLoadout.empty());

        List<String> options = new ArrayList<>();
        options.add("(default)");
        options.addAll(allArrowItemIds);

        String current = mobLoadout.arrowOverride;
        String currentDisplay = (current == null || current.isBlank()) ? "(default)" : current;

        Minecraft.getInstance().setScreen(new StringSelectScreen(
            this,
            Component.literal("Select Mob Arrow Override"),
            options,
            currentDisplay,
            selected -> {
                if (selected == null) {
                    return;
                }
                String s = selected.trim();
                if (s.equalsIgnoreCase("(default)")) {
                    mobLoadout.arrowOverride = "";
                } else {
                    mobLoadout.arrowOverride = normalizeSelection(s);
                }
                refreshRightPanel();
            }
        ));
    }

    private void saveAndClose() {
        try {
            writeConfigFile();
            // Apply immediately (no restart required)
            GANCityMod.reloadLoadoutsFromDisk();
        } catch (Exception e) {
            GANCityMod.LOGGER.warn("Failed to write loadout config: {}", e.toString());
        }
        onClose();
    }

    private void loadFromConfigFile() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            return;
        }

        try {
            TomlLoadouts parsed = TomlLoadouts.read(configPath);
            globalLoadoutsDisabled = parsed.globalLoadoutsDisabled;
            defaultArrowId = parsed.defaultArrowId;

            loadouts.clear();
            for (Map.Entry<String, List<String>> e : parsed.mobWeapons.entrySet()) {
                MobLoadout ml = MobLoadout.empty();
                for (int i = 0; i < 5; i++) {
                    ml.weapons.set(i, i < e.getValue().size() ? e.getValue().get(i) : "");
                }
                loadouts.put(e.getKey(), ml);
            }
            for (Map.Entry<String, String> e : parsed.mobArrowOverrides.entrySet()) {
                loadouts.computeIfAbsent(e.getKey(), k -> MobLoadout.empty()).arrowOverride = e.getValue();
            }
        } catch (Exception e) {
            GANCityMod.LOGGER.warn("Failed to parse loadout config: {}", e.toString());
        }
    }

    private void writeConfigFile() throws IOException {
        Path configPath = getConfigPath();
        Files.createDirectories(configPath.getParent());

        // Build compact data to write
        Map<String, List<String>> mobWeapons = new LinkedHashMap<>();
        Map<String, String> mobArrowOverrides = new LinkedHashMap<>();

        List<String> mobKeys = loadouts.keySet().stream().sorted().toList();
        for (String mobId : mobKeys) {
            MobLoadout ml = loadouts.get(mobId);
            if (ml == null) {
                continue;
            }
            List<String> weapons = ml.weapons.stream()
                .map(AdaptiveMobAiLoadoutConfigScreen::normalizeSelection)
                .filter(s -> s != null && !s.isBlank())
                .limit(5)
                .toList();
            if (!weapons.isEmpty()) {
                mobWeapons.put(mobId, weapons);
            }

            String arrow = normalizeSelection(ml.arrowOverride);
            if (arrow != null && !arrow.isBlank()) {
                mobArrowOverrides.put(mobId, arrow);
            }
        }

        TomlLoadouts.write(configPath, mobWeapons, globalLoadoutsDisabled, defaultArrowId, mobArrowOverrides);
    }

    private static String normalizeSelection(String selected) {
        if (selected == null) {
            return "";
        }
        String s = selected.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.equalsIgnoreCase("(empty)") || s.equalsIgnoreCase("(learned)") || s.equalsIgnoreCase("(default)")) {
            return "";
        }
        if (s.equalsIgnoreCase("default") || s.equalsIgnoreCase("preserve")) {
            return "default";
        }
        if (s.equalsIgnoreCase("none")) {
            return "none";
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (!lower.contains(":")) {
            lower = "minecraft:" + lower;
        }
        return lower;
    }

    private static List<String> buildMobIdList(java.util.Set<String> configuredMobIds) {
        try {
            java.util.Set<String> configured = configuredMobIds == null ? java.util.Set.of() : new java.util.HashSet<>(configuredMobIds);
            boolean iceAndFireLoaded = isModLoaded("iceandfire");

            // Show all vanilla + modded hostile mobs by default, plus any mobs already configured in the file.
            // This prevents the config screen from "missing" mobs that the server-side logic supports.
            return ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .sorted()
                .filter(key -> {
                    String id = key.toString();
                    if (configured.contains(id)) {
                        return true;
                    }
                    if (iceAndFireLoaded && id.startsWith("iceandfire:")) {
                        return false;
                    }

                    EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(key);
                    if (type == null) {
                        return false;
                    }
                    return type.getCategory() == MobCategory.MONSTER;
                })
                .map(ResourceLocation::toString)
                .collect(Collectors.toList());
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static boolean isModLoaded(String modId) {
        try {
            return ModList.get().isLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List<String> buildWeaponItemIdList() {
        try {
            return ForgeRegistries.ITEMS.getKeys().stream()
                .sorted()
                .map(id -> {
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    if (item == null) {
                        return null;
                    }

                    // Keep the picker focused: only items that are clearly combat gear.
                    if (item instanceof net.minecraft.world.item.SwordItem) {
                        return id.toString();
                    }
                    if (item instanceof net.minecraft.world.item.AxeItem) {
                        return id.toString();
                    }
                    if (item instanceof net.minecraft.world.item.BowItem) {
                        return id.toString();
                    }
                    if (item instanceof net.minecraft.world.item.CrossbowItem) {
                        return id.toString();
                    }
                    if (item instanceof net.minecraft.world.item.TridentItem) {
                        return id.toString();
                    }
                    if (item instanceof net.minecraft.world.item.ShieldItem) {
                        return id.toString();
                    }

                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static List<String> buildArrowItemIdList() {
        try {
            return ForgeRegistries.ITEMS.getKeys().stream()
                .sorted()
                .map(id -> {
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    if (item instanceof net.minecraft.world.item.ArrowItem) {
                        return id.toString();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Throwable ignored) {
            // Conservative defaults
            return List.of("minecraft:arrow", "minecraft:spectral_arrow", "minecraft:tipped_arrow");
        }
    }

    public static boolean isGlobalLoadoutsDisabled() {
        try {
            Path configPath = getConfigPath();
            if (!Files.exists(configPath)) {
                return false;
            }
            return TomlLoadouts.read(configPath).globalLoadoutsDisabled;
        } catch (Exception e) {
            GANCityMod.LOGGER.warn("Failed to read global loadout toggle: {}", e.toString());
            return false;
        }
    }

    public static boolean toggleGlobalLoadoutsDisabled() {
        boolean next = !isGlobalLoadoutsDisabled();
        setGlobalLoadoutsDisabled(next);
        return next;
    }

    public static void setGlobalLoadoutsDisabled(boolean disabled) {
        try {
            Path configPath = getConfigPath();
            Files.createDirectories(configPath.getParent());

            TomlLoadouts parsed = Files.exists(configPath)
                ? TomlLoadouts.read(configPath)
                : new TomlLoadouts(Map.of(), false, "minecraft:arrow", Map.of());

            String defaultArrow = "minecraft:arrow";
            if (parsed.defaultArrowId != null && !parsed.defaultArrowId.isBlank()) {
                defaultArrow = parsed.defaultArrowId;
            }

            TomlLoadouts.write(configPath, parsed.mobWeapons, disabled, defaultArrow, parsed.mobArrowOverrides);
            GANCityMod.reloadLoadoutsFromDisk();
        } catch (Exception e) {
            GANCityMod.LOGGER.warn("Failed to update global loadout toggle: {}", e.toString());
        }
    }

    private static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
    }

    private static final class MobLoadout {
        final List<String> weapons;
        String arrowOverride;

        private MobLoadout(List<String> weapons, String arrowOverride) {
            this.weapons = weapons;
            this.arrowOverride = arrowOverride;
        }

        static MobLoadout empty() {
            List<String> w = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                w.add("");
            }
            return new MobLoadout(w, "");
        }
    }

    private final class MobList extends ObjectSelectionList<MobList.Entry> {
        MobList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
            super(minecraft, width, height, y0, y1, itemHeight);
            setRenderBackground(true);
            replaceEntries(allMobIds);
        }

        void replaceEntries(List<String> mobIds) {
            clearEntries();
            for (String mobId : mobIds) {
                addEntry(new Entry(mobId));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String mobId;

            Entry(String mobId) {
                this.mobId = Objects.requireNonNull(mobId, "mobId");
            }

            @Override
            public Component getNarration() {
                return Component.literal(mobId);
            }

            @Override
            public void render(@Nonnull net.minecraft.client.gui.GuiGraphics gfx, int index, int y, int x, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
                int color = Objects.equals(selectedMobId, mobId) ? 0xFFFFCC00 : 0xFFE0E0E0;
                gfx.drawString(font, mobId, x + 4, y + 4, color, false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                setSelectedMob(mobId);
                return true;
            }
        }
    }

    private static final class TomlLoadouts {
        final Map<String, List<String>> mobWeapons;
        final boolean globalLoadoutsDisabled;
        final String defaultArrowId;
        final Map<String, String> mobArrowOverrides;

        private TomlLoadouts(Map<String, List<String>> mobWeapons, boolean globalLoadoutsDisabled, String defaultArrowId, Map<String, String> mobArrowOverrides) {
            this.mobWeapons = mobWeapons;
            this.globalLoadoutsDisabled = globalLoadoutsDisabled;
            this.defaultArrowId = defaultArrowId;
            this.mobArrowOverrides = mobArrowOverrides;
        }

        static TomlLoadouts read(Path path) throws IOException {
            List<String> lines = Files.readAllLines(path);
            Map<String, String> kv = new HashMap<>();
            for (String rawLine : lines) {
                String line = rawLine;
                int hashIdx = line.indexOf('#');
                if (hashIdx >= 0) {
                    line = line.substring(0, hashIdx);
                }
                line = line.trim();
                if (line.isEmpty() || line.startsWith("[")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }
                String key = line.substring(0, eqIdx).trim();
                String value = line.substring(eqIdx + 1).trim();
                kv.put(key, value);
            }

            List<String> loadouts = parseStringList(kv.get("mobWeaponLoadouts"));
            Map<String, List<String>> mobWeapons = parseMobWeaponLoadouts(loadouts);
            boolean globalLoadoutsDisabled = parseBoolean(kv.get("disableLoadoutsGlobally"), false);

            String defaultArrow = stripQuotes(Optional.ofNullable(kv.get("defaultBowArrowItem")).orElse("\"minecraft:arrow\"")).trim();
            if (defaultArrow.isEmpty()) {
                defaultArrow = "minecraft:arrow";
            }

            List<String> arrowOverrides = parseStringList(kv.get("mobBowArrowOverrides"));
            Map<String, String> mobArrows = parseKeyValueMap(arrowOverrides);

            return new TomlLoadouts(mobWeapons, globalLoadoutsDisabled, defaultArrow, mobArrows);
        }

        static void write(Path path, Map<String, List<String>> mobWeapons, boolean globalLoadoutsDisabled, String defaultArrowId, Map<String, String> mobArrowOverrides) throws IOException {
            List<String> lines = Files.exists(path) ? Files.readAllLines(path) : new ArrayList<>();

            int start = findSectionStart(lines, "[loadouts]");
            if (start < 0) {
                // Insert before [mob_behaviors] if present, else append.
                int insertAt = findFirstSection(lines, "[mob_behaviors]");
                if (insertAt < 0) {
                    insertAt = lines.size();
                    if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                        lines.add("");
                    }
                }
                lines.addAll(insertAt, buildLoadoutsBlock(mobWeapons, globalLoadoutsDisabled, defaultArrowId, mobArrowOverrides));
            } else {
                int end = findSectionEnd(lines, start);
                List<String> block = buildLoadoutsBlock(mobWeapons, globalLoadoutsDisabled, defaultArrowId, mobArrowOverrides);
                lines.subList(start, end).clear();
                lines.addAll(start, block);
            }

            Files.write(path, lines);
        }

        private static int findFirstSection(List<String> lines, String sectionHeader) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals(sectionHeader)) {
                    return i;
                }
            }
            return -1;
        }

        private static int findSectionStart(List<String> lines, String sectionHeader) {
            return findFirstSection(lines, sectionHeader);
        }

        private static int findSectionEnd(List<String> lines, int start) {
            for (int i = start + 1; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.startsWith("[") && t.endsWith("]") && !t.startsWith("[loadouts")) {
                    return i;
                }
            }
            return lines.size();
        }

        private static List<String> buildLoadoutsBlock(Map<String, List<String>> mobWeapons, boolean globalLoadoutsDisabled, String defaultArrowId, Map<String, String> mobArrowOverrides) {
            List<String> out = new ArrayList<>();
            out.add("[loadouts]");
            out.add("disableLoadoutsGlobally = " + globalLoadoutsDisabled);

            List<String> entries = mobWeapons.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    String csv = String.join(",", e.getValue());
                    return quote(e.getKey() + "=" + csv);
                })
                .toList();
            out.add("mobWeaponLoadouts = [" + String.join(", ", entries) + "]");
            out.add("");

            out.add("[loadouts.bows]");
            String arrow = (defaultArrowId == null || defaultArrowId.isBlank()) ? "minecraft:arrow" : defaultArrowId;
            out.add("defaultBowArrowItem = " + quote(arrow));

            List<String> arrowEntries = mobArrowOverrides.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> quote(e.getKey() + "=" + e.getValue()))
                .toList();
            out.add("mobBowArrowOverrides = [" + String.join(", ", arrowEntries) + "]");
            out.add("");

            return out;
        }

        private static boolean parseBoolean(String raw, boolean defaultValue) {
            if (raw == null) {
                return defaultValue;
            }
            String normalized = stripQuotes(raw).trim();
            if (normalized.equalsIgnoreCase("true")) {
                return true;
            }
            if (normalized.equalsIgnoreCase("false")) {
                return false;
            }
            return defaultValue;
        }

        private static String quote(String s) {
            String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + escaped + "\"";
        }

        private static List<String> parseStringList(String raw) {
            if (raw == null) {
                return List.of();
            }
            String v = raw.trim();
            if (v.isEmpty()) {
                return List.of();
            }
            if (!v.startsWith("[") || !v.endsWith("]")) {
                String single = stripQuotes(v).trim();
                return single.isEmpty() ? List.of() : List.of(single);
            }

            String inner = v.substring(1, v.length() - 1).trim();
            if (inner.isEmpty()) {
                return List.of();
            }

            List<String> out = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            char quoteChar = 0;
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (inQuotes) {
                    if (c == quoteChar) {
                        inQuotes = false;
                    } else {
                        current.append(c);
                    }
                    continue;
                }

                if (c == '"' || c == '\'') {
                    inQuotes = true;
                    quoteChar = c;
                    continue;
                }

                if (c == ',') {
                    String token = stripQuotes(current.toString()).trim();
                    if (!token.isEmpty()) {
                        out.add(token);
                    }
                    current.setLength(0);
                    continue;
                }

                current.append(c);
            }

            String token = stripQuotes(current.toString()).trim();
            if (!token.isEmpty()) {
                out.add(token);
            }

            return out;
        }

        private static Map<String, List<String>> parseMobWeaponLoadouts(List<String> entries) {
            if (entries == null || entries.isEmpty()) {
                return Map.of();
            }
            Map<String, List<String>> out = new HashMap<>();
            for (String entry : entries) {
                if (entry == null) {
                    continue;
                }
                String t = entry.trim();
                int eq = t.indexOf('=');
                if (eq <= 0 || eq >= t.length() - 1) {
                    continue;
                }
                String mobId = t.substring(0, eq).trim();
                String csv = t.substring(eq + 1).trim();
                if (mobId.isEmpty() || csv.isEmpty()) {
                    continue;
                }
                List<String> weapons = new ArrayList<>();
                for (String w : csv.split(",")) {
                    if (weapons.size() >= 5) {
                        break;
                    }
                    String weapon = w == null ? "" : w.trim();
                    if (weapon.isEmpty()) {
                        continue;
                    }
                    weapons.add(weapon);
                }
                if (!weapons.isEmpty()) {
                    out.put(mobId, weapons);
                }
            }
            return out;
        }

        private static Map<String, String> parseKeyValueMap(List<String> entries) {
            if (entries == null || entries.isEmpty()) {
                return Map.of();
            }
            Map<String, String> out = new HashMap<>();
            for (String entry : entries) {
                if (entry == null) {
                    continue;
                }
                String t = entry.trim();
                int eq = t.indexOf('=');
                if (eq <= 0 || eq >= t.length() - 1) {
                    continue;
                }
                String key = t.substring(0, eq).trim();
                String value = t.substring(eq + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    out.put(key, value);
                }
            }
            return out;
        }

        private static String stripQuotes(String value) {
            if (value == null) {
                return "";
            }
            String v = value.trim();
            if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                return v.substring(1, v.length() - 1);
            }
            return v;
        }
    }
}
