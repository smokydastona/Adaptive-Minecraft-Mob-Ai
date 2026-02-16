package com.minecraft.gancity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecraft.gancity.GANCityMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Server-side persisted per-player configuration for what weapons mobs should be assigned
 * when spawning near that player.
 */
public final class PlayerMobLoadoutStore {

    public static final String DEFAULT_MOB_KEY = "default";

    public static final class WeaponOption {
        /** Item id like "minecraft:iron_sword" or "none" for unarmed. */
        public String item = "minecraft:stone_sword";
        /** Relative weight (chance) for selection. */
        public double weight = 1.0;
    }

    public static final class MobLoadout {
        public boolean enabled = true;
        public List<WeaponOption> options = new ArrayList<>();
    }

    public static final class PlayerLoadout {
        public boolean enabled = true;

        /** Per-mob-type loadouts, keyed by entity type id like "minecraft:zombie". */
        public Map<String, MobLoadout> mobs = new HashMap<>();

        // Backward compatibility (older config format). If present, it is migrated into DEFAULT_MOB_KEY.
        public List<String> any = new ArrayList<>();
        public List<String> melee = new ArrayList<>();
        public List<String> ranged = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerLoadout>>() {}.getType();
    private static final Object LOCK = new Object();

    private static volatile boolean loaded = false;
    private static Map<String, PlayerLoadout> byPlayer = new HashMap<>();

    private PlayerMobLoadoutStore() {
    }

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("adaptivemobai-player-loadouts.json");
    }

    /**
     * Ensures the per-player loadout config file exists on disk so users can edit it
     * even before any commands are run.
     */
    public static void ensureFileExists() {
        synchronized (LOCK) {
            Path file = configFile();
            try {
                Files.createDirectories(file.getParent());
                if (!Files.exists(file)) {
                    try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                        writer.write("{}\n");
                    }
                }
            } catch (IOException e) {
                GANCityMod.LOGGER.warn("Failed to create player loadout config ({}): {}", file, e.toString());
            }
        }
    }

    public static void loadIfNeeded() {
        if (loaded) {
            return;
        }
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            loaded = true;

            // Create an empty file up-front so users can edit it without using commands.
            ensureFileExists();

            Path file = configFile();
            if (!Files.exists(file)) {
                byPlayer = new HashMap<>();
                return;
            }

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, PlayerLoadout> parsed = GSON.fromJson(reader, MAP_TYPE);
                byPlayer = parsed != null ? parsed : new HashMap<>();

                // Post-load sanitation + migration from legacy lists.
                for (PlayerLoadout loadout : byPlayer.values()) {
                    if (loadout == null) {
                        continue;
                    }
                    if (loadout.mobs == null) {
                        loadout.mobs = new HashMap<>();
                    }
                    migrateLegacyListsIfPresent(loadout);
                }
            } catch (Exception e) {
                GANCityMod.LOGGER.warn("Failed to read player loadout config ({}): {}", file, e.toString());
                byPlayer = new HashMap<>();
            }
        }
    }

    private static void save() {
        synchronized (LOCK) {
            Path file = configFile();
            try {
                Files.createDirectories(file.getParent());
                try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(byPlayer, MAP_TYPE, writer);
                }
            } catch (IOException e) {
                GANCityMod.LOGGER.warn("Failed to write player loadout config ({}): {}", file, e.toString());
            }
        }
    }

    public static PlayerLoadout getOrCreate(UUID playerId) {
        loadIfNeeded();
        synchronized (LOCK) {
            return byPlayer.computeIfAbsent(playerId.toString(), k -> new PlayerLoadout());
        }
    }

    public static MobLoadout getOrCreateMob(UUID playerId, String mobTypeId) {
        PlayerLoadout loadout = getOrCreate(playerId);
        synchronized (LOCK) {
            if (loadout.mobs == null) {
                loadout.mobs = new HashMap<>();
            }
            String normalizedMob = normalizeMobId(mobTypeId);
            return loadout.mobs.computeIfAbsent(normalizedMob, k -> new MobLoadout());
        }
    }

    public static Optional<PlayerLoadout> get(UUID playerId) {
        loadIfNeeded();
        synchronized (LOCK) {
            return Optional.ofNullable(byPlayer.get(playerId.toString()));
        }
    }

    public static void setEnabled(UUID playerId, boolean enabled) {
        PlayerLoadout loadout;
        synchronized (LOCK) {
            loadout = getOrCreate(playerId);
            loadout.enabled = enabled;
        }
        save();
    }

    public static void reset(UUID playerId) {
        loadIfNeeded();
        synchronized (LOCK) {
            byPlayer.remove(playerId.toString());
        }
        save();
    }

    public static boolean add(UUID playerId, String category, String itemId) {
        PlayerLoadout loadout;
        synchronized (LOCK) {
            loadout = getOrCreate(playerId);
            List<String> list = listFor(loadout, category);
            if (list == null) {
                return false;
            }
            String normalized = normalizeItemId(itemId);
            if (normalized == null) {
                return false;
            }
            if (!list.contains(normalized)) {
                list.add(normalized);
            }
        }
        save();
        return true;
    }

    public static boolean remove(UUID playerId, String category, String itemId) {
        PlayerLoadout loadout;
        synchronized (LOCK) {
            loadout = getOrCreate(playerId);
            List<String> list = listFor(loadout, category);
            if (list == null) {
                return false;
            }
            String normalized = normalizeItemId(itemId);
            if (normalized == null) {
                return false;
            }
            list.removeIf(s -> s.equals(normalized));
        }
        save();
        return true;
    }

    public static boolean clear(UUID playerId, String category) {
        PlayerLoadout loadout;
        synchronized (LOCK) {
            loadout = getOrCreate(playerId);
            List<String> list = listFor(loadout, category);
            if (list == null) {
                return false;
            }
            list.clear();
        }
        save();
        return true;
    }

    public static boolean setMobEnabled(UUID playerId, String mobTypeId, boolean enabled) {
        synchronized (LOCK) {
            MobLoadout mob = getOrCreateMob(playerId, mobTypeId);
            mob.enabled = enabled;
        }
        save();
        return true;
    }

    public static boolean addOption(UUID playerId, String mobTypeId, String itemId, double weight) {
        if (Double.isNaN(weight) || Double.isInfinite(weight) || weight <= 0.0) {
            return false;
        }

        synchronized (LOCK) {
            MobLoadout mob = getOrCreateMob(playerId, mobTypeId);
            if (mob.options == null) {
                mob.options = new ArrayList<>();
            }

            String normalizedItem = normalizeWeaponToken(itemId);
            if (normalizedItem == null) {
                return false;
            }

            // Upsert by item id.
            for (WeaponOption opt : mob.options) {
                if (opt != null && normalizedItem.equalsIgnoreCase(String.valueOf(opt.item))) {
                    opt.item = normalizedItem;
                    opt.weight = weight;
                    save();
                    return true;
                }
            }

            WeaponOption opt = new WeaponOption();
            opt.item = normalizedItem;
            opt.weight = weight;
            mob.options.add(opt);
        }

        save();
        return true;
    }

    public static boolean removeOption(UUID playerId, String mobTypeId, String itemId) {
        synchronized (LOCK) {
            MobLoadout mob = getOrCreateMob(playerId, mobTypeId);
            if (mob.options == null) {
                return true;
            }
            String normalizedItem = normalizeWeaponToken(itemId);
            if (normalizedItem == null) {
                return false;
            }
            mob.options.removeIf(o -> o != null && normalizedItem.equalsIgnoreCase(String.valueOf(o.item)));
        }
        save();
        return true;
    }

    public static boolean clearOptions(UUID playerId, String mobTypeId) {
        synchronized (LOCK) {
            MobLoadout mob = getOrCreateMob(playerId, mobTypeId);
            if (mob.options != null) {
                mob.options.clear();
            }
        }
        save();
        return true;
    }

    public enum WeaponDecisionType {
        /** No configured override was found; caller may apply mod defaults. */
        NO_OVERRIDE,
        /** Explicitly preserve the mob's existing equipment (do not set slots). */
        PRESERVE,
        /** Explicitly unarmed. */
        UNARMED,
        /** Explicitly set to a specific weapon. */
        SET
    }

    public static final class WeaponDecision {
        public final WeaponDecisionType type;
        public final ItemStack weapon;

        private WeaponDecision(WeaponDecisionType type, ItemStack weapon) {
            this.type = type;
            this.weapon = weapon;
        }

        public static WeaponDecision noOverride() {
            return new WeaponDecision(WeaponDecisionType.NO_OVERRIDE, null);
        }

        public static WeaponDecision preserve() {
            return new WeaponDecision(WeaponDecisionType.PRESERVE, null);
        }

        public static WeaponDecision unarmed() {
            return new WeaponDecision(WeaponDecisionType.UNARMED, ItemStack.EMPTY);
        }

        public static WeaponDecision set(ItemStack weapon) {
            return new WeaponDecision(WeaponDecisionType.SET, weapon);
        }
    }

    /**
     * Returns:
     * - NO_OVERRIDE: no configured override (caller may use mod default behavior)
     * - PRESERVE: explicitly do not touch the mob's equipment (compatibility mode)
     * - UNARMED: explicitly unarmed
     * - SET: configured weapon
     */
    public static WeaponDecision chooseWeaponFor(Player player, String mobTypeId, Random random) {
        if (player == null) {
            return WeaponDecision.noOverride();
        }

        PlayerLoadout loadout = get(player.getUUID()).orElse(null);
        if (loadout == null || !loadout.enabled) {
            return WeaponDecision.noOverride();
        }
        if (loadout.mobs == null || loadout.mobs.isEmpty()) {
            return WeaponDecision.noOverride();
        }

        String normalizedMob = normalizeMobId(mobTypeId);
        MobLoadout mobLoadout = loadout.mobs.get(normalizedMob);
        if (mobLoadout == null) {
            mobLoadout = loadout.mobs.get(DEFAULT_MOB_KEY);
        }
        if (mobLoadout == null || !mobLoadout.enabled) {
            return WeaponDecision.noOverride();
        }

        List<WeaponOption> options = mobLoadout.options;
        if (options == null || options.isEmpty()) {
            return WeaponDecision.noOverride();
        }

        // Build a list of valid options (including preserve/unarmed).
        List<WeaponOption> valid = new ArrayList<>();
        double total = 0.0;
        for (WeaponOption opt : options) {
            if (opt == null || opt.weight <= 0.0 || opt.item == null) {
                continue;
            }

            String token = opt.item.trim();
            if (isPreserveToken(token)) {
                valid.add(opt);
                total += opt.weight;
                continue;
            }
            if (isUnarmedToken(token)) {
                valid.add(opt);
                total += opt.weight;
                continue;
            }

            String normalizedItem = normalizeItemId(token);
            if (normalizedItem == null) {
                continue;
            }
            if (resolveItem(normalizedItem) == null) {
                continue;
            }

            // Keep the original opt but ensure item is normalized.
            opt.item = normalizedItem;
            valid.add(opt);
            total += opt.weight;
        }

        if (valid.isEmpty() || total <= 0.0) {
            return WeaponDecision.noOverride();
        }

        double roll = random.nextDouble() * total;
        for (WeaponOption opt : valid) {
            roll -= opt.weight;
            if (roll <= 0.0) {
                if (opt.item != null) {
                    if (isPreserveToken(opt.item)) {
                        return WeaponDecision.preserve();
                    }
                    if (isUnarmedToken(opt.item)) {
                        return WeaponDecision.unarmed();
                    }
                }
                Item item = resolveItem(opt.item);
                return item == null ? WeaponDecision.noOverride() : WeaponDecision.set(new ItemStack(item));
            }
        }

        // Fallback to last.
        WeaponOption opt = valid.get(valid.size() - 1);
        if (opt.item != null) {
            if (isPreserveToken(opt.item)) {
                return WeaponDecision.preserve();
            }
            if (isUnarmedToken(opt.item)) {
                return WeaponDecision.unarmed();
            }
        }
        Item item = resolveItem(opt.item);
        return item == null ? WeaponDecision.noOverride() : WeaponDecision.set(new ItemStack(item));
    }

    public static ItemStack defaultWeapon(Random random) {
        int roll = random.nextInt(4);
        if (roll == 0) {
            return Items.BOW.getDefaultInstance();
        } else if (roll == 1) {
            return Items.CROSSBOW.getDefaultInstance();
        } else if (roll == 2) {
            return Items.TRIDENT.getDefaultInstance();
        }
        return Items.STONE_SWORD.getDefaultInstance();
    }

    private static List<String> listFor(PlayerLoadout loadout, String category) {
        String c = category == null ? "" : category.toLowerCase(Locale.ROOT);
        return switch (c) {
            case "any" -> loadout.any;
            case "melee" -> loadout.melee;
            case "ranged" -> loadout.ranged;
            default -> null;
        };
    }

    private static List<String> safeList(List<String> list) {
        return list == null ? java.util.Collections.emptyList() : list;
    }


    private static void migrateLegacyListsIfPresent(PlayerLoadout loadout) {
        if (loadout == null) {
            return;
        }
        if (loadout.mobs == null) {
            loadout.mobs = new HashMap<>();
        }
        // If the new map is already populated, don't override.
        if (!loadout.mobs.isEmpty()) {
            return;
        }

        List<String> any = safeList(loadout.any);
        List<String> melee = safeList(loadout.melee);
        List<String> ranged = safeList(loadout.ranged);
        if (any.isEmpty() && melee.isEmpty() && ranged.isEmpty()) {
            return;
        }

        MobLoadout mob = new MobLoadout();
        mob.enabled = true;
        mob.options = new ArrayList<>();

        // Legacy lists had no weights; assign equal weight=1.
        for (String id : any) {
            addLegacyOption(mob, id);
        }
        for (String id : melee) {
            addLegacyOption(mob, id);
        }
        for (String id : ranged) {
            addLegacyOption(mob, id);
        }

        if (!mob.options.isEmpty()) {
            loadout.mobs.put(DEFAULT_MOB_KEY, mob);
        }
    }

    private static void addLegacyOption(MobLoadout mob, String itemId) {
        if (mob == null || itemId == null || itemId.isBlank()) {
            return;
        }
        String normalized = normalizeItemId(itemId);
        if (normalized == null) {
            return;
        }
        WeaponOption opt = new WeaponOption();
        opt.item = normalized;
        opt.weight = 1.0;
        mob.options.add(opt);
    }

    private static String normalizeMobId(String mobTypeId) {
        if (mobTypeId == null || mobTypeId.isBlank()) {
            return DEFAULT_MOB_KEY;
        }
        ResourceLocation rl = ResourceLocation.tryParse(mobTypeId);
        if (rl == null) {
            rl = ResourceLocation.tryParse("minecraft:" + mobTypeId);
        }
        return rl == null ? DEFAULT_MOB_KEY : rl.toString();
    }

    private static boolean isUnarmedToken(String token) {
        if (token == null) {
            return false;
        }
        String t = token.trim().toLowerCase(Locale.ROOT);
        return t.equals("none") || t.equals("unarmed") || t.equals("empty") || t.equals("air");
    }

    private static boolean isPreserveToken(String token) {
        if (token == null) {
            return false;
        }
        String t = token.trim().toLowerCase(Locale.ROOT);
        return t.equals("default") || t.equals("preserve");
    }

    private static String normalizeWeaponToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (isPreserveToken(token)) {
            return "default";
        }
        if (isUnarmedToken(token)) {
            return "none";
        }
        return normalizeItemId(token);
    }

    private static String normalizeItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) {
            // Allow users to type "diamond_sword".
            rl = ResourceLocation.tryParse("minecraft:" + itemId);
        }
        if (rl == null) {
            return null;
        }
        return rl.toString();
    }

    private static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) {
            return null;
        }

        try {
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null || item == Items.AIR) {
                return null;
            }
            return item;
        } catch (Throwable t) {
            return null;
        }
    }
}
