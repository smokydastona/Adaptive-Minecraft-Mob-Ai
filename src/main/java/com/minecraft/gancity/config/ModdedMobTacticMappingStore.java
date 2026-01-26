package com.minecraft.gancity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minecraft.gancity.GANCityMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Persisted mapping configuration for modded mobs.
 *
 * Purpose:
 * - Allow modded mobs (non-minecraft namespace) to be mapped to an existing vanilla tactic profile key
 *   (e.g., "zombie", "skeleton", "cow") for action selection.
 * - Keep an auto-mapping default, with per-mob overrides for fine tuning.
 */
public final class ModdedMobTacticMappingStore {

    public static final class Config {
        /** Master switch for applying mapping logic to non-minecraft entity types. */
        public boolean enabled = true;

        /** If true, unknown/modded mobs will be automatically mapped to a vanilla profile when possible. */
        public boolean autoAssignEnabled = true;

        /** Fallback profile key for hostile mobs when no better mapping exists. */
        public String defaultHostileProfile = "zombie";

        /** Fallback profile key for non-hostile mobs when no better mapping exists. */
        public String defaultPassiveProfile = "cow";

        /** Per-entity override: entityTypeId ("modid:entity") -> profile key ("zombie", "cow", etc.). */
        public Map<String, String> overrides = new HashMap<>();

        /** Optional per-namespace default: modId -> profile key. */
        public Map<String, String> namespaceDefaults = new HashMap<>();
    }

    private static final String FILE_NAME = "adaptivemobai-modded-mob-tactics.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    private static volatile boolean loaded;
    private static Config config = new Config();

    private ModdedMobTacticMappingStore() {
    }

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static void ensureFileExists() {
        synchronized (LOCK) {
            Path file = configFile();
            try {
                Files.createDirectories(file.getParent());
                if (!Files.exists(file)) {
                    try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                        GSON.toJson(new Config(), writer);
                        writer.write("\n");
                    }
                }
            } catch (Exception e) {
                GANCityMod.LOGGER.warn("Failed to create modded-mob tactics config ({}): {}", file, e.toString());
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
            ensureFileExists();

            Path file = configFile();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Config parsed = GSON.fromJson(reader, Config.class);
                config = parsed != null ? parsed : new Config();
            } catch (Exception e) {
                GANCityMod.LOGGER.warn("Failed to read modded-mob tactics config ({}): {}", file, e.toString());
                config = new Config();
            }

            sanitize();
        }
    }

    private static void sanitize() {
        if (config == null) {
            config = new Config();
        }
        if (config.overrides == null) {
            config.overrides = new HashMap<>();
        }
        if (config.namespaceDefaults == null) {
            config.namespaceDefaults = new HashMap<>();
        }
        if (config.defaultHostileProfile == null || config.defaultHostileProfile.isBlank()) {
            config.defaultHostileProfile = "zombie";
        }
        if (config.defaultPassiveProfile == null || config.defaultPassiveProfile.isBlank()) {
            config.defaultPassiveProfile = "cow";
        }
    }

    public static Config get() {
        loadIfNeeded();
        synchronized (LOCK) {
            return config;
        }
    }

    public static void save() {
        loadIfNeeded();
        synchronized (LOCK) {
            Path file = configFile();
            try {
                Files.createDirectories(file.getParent());
                try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(config, writer);
                    writer.write("\n");
                }
            } catch (Exception e) {
                GANCityMod.LOGGER.warn("Failed to write modded-mob tactics config ({}): {}", file, e.toString());
            }
        }
    }

    public static void setEnabled(boolean enabled) {
        loadIfNeeded();
        synchronized (LOCK) {
            config.enabled = enabled;
        }
        save();
    }

    public static void setAutoAssignEnabled(boolean enabled) {
        loadIfNeeded();
        synchronized (LOCK) {
            config.autoAssignEnabled = enabled;
        }
        save();
    }

    public static Optional<String> getOverride(String entityTypeId) {
        loadIfNeeded();
        if (entityTypeId == null || entityTypeId.isBlank()) {
            return Optional.empty();
        }
        synchronized (LOCK) {
            String value = config.overrides.get(entityTypeId);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(value);
        }
    }

    public static Optional<String> getNamespaceDefault(String namespace) {
        loadIfNeeded();
        if (namespace == null || namespace.isBlank()) {
            return Optional.empty();
        }
        synchronized (LOCK) {
            String value = config.namespaceDefaults.get(namespace);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(value);
        }
    }

    public static void setOverride(String entityTypeId, String profileKeyOrNullToClear) {
        loadIfNeeded();
        if (entityTypeId == null || entityTypeId.isBlank()) {
            return;
        }

        synchronized (LOCK) {
            if (profileKeyOrNullToClear == null || profileKeyOrNullToClear.isBlank()) {
                config.overrides.remove(entityTypeId);
                return;
            }

            // Normalize: allow users/UI to pass "minecraft:zombie" and store "zombie".
            String normalized = normalizeProfileKey(profileKeyOrNullToClear);
            if (normalized == null || normalized.isBlank()) {
                config.overrides.remove(entityTypeId);
            } else {
                config.overrides.put(entityTypeId, normalized);
            }
        }

        save();
    }

    public static void setNamespaceDefault(String namespace, String profileKeyOrNullToClear) {
        loadIfNeeded();
        if (namespace == null || namespace.isBlank()) {
            return;
        }

        synchronized (LOCK) {
            if (profileKeyOrNullToClear == null || profileKeyOrNullToClear.isBlank()) {
                config.namespaceDefaults.remove(namespace);
                return;
            }
            String normalized = normalizeProfileKey(profileKeyOrNullToClear);
            if (normalized == null || normalized.isBlank()) {
                config.namespaceDefaults.remove(namespace);
            } else {
                config.namespaceDefaults.put(namespace, normalized);
            }
        }

        save();
    }

    public static String normalizeProfileKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(trimmed);
        if (rl != null) {
            if ("minecraft".equals(rl.getNamespace())) {
                return rl.getPath();
            }
            // For non-minecraft namespaces, we still treat the input as a profile key, not an entity id.
            return rl.toString();
        }
        return trimmed;
    }
}
