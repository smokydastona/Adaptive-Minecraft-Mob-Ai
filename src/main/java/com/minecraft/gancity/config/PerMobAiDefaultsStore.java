package com.minecraft.gancity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minecraft.gancity.GANCityMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-entity AI enable/disable overrides.
 *
 * This allows server owners to keep specific mobs (vanilla or modded) on their
 * default/vanilla AI by preventing this mod from injecting AI goals.
 */
public final class PerMobAiDefaultsStore {

    public static final class Config {
        /** Master switch for applying per-mob overrides. */
        public boolean enabled = true;

        /** EntityType id ("namespace:path") -> whether this mod's AI is enabled for that mob. */
        public Map<String, Boolean> aiEnabledOverrides = new HashMap<>();
    }

    private static final String FILE_NAME = "adaptivemobai-per-mob-ai.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    private static volatile boolean loaded;
    private static Config config = new Config();

    private PerMobAiDefaultsStore() {
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
                GANCityMod.LOGGER.warn("Failed to create per-mob AI config ({}): {}", file, e.toString());
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
                GANCityMod.LOGGER.warn("Failed to read per-mob AI config ({}): {}", file, e.toString());
                config = new Config();
            }

            sanitize();
        }
    }

    private static void sanitize() {
        if (config == null) {
            config = new Config();
        }
        if (config.aiEnabledOverrides == null) {
            config.aiEnabledOverrides = new HashMap<>();
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
                GANCityMod.LOGGER.warn("Failed to write per-mob AI config ({}): {}", file, e.toString());
            }
        }
    }

    /**
     * Returns true if AI enhancements should be applied for this entity type.
     * Default behavior is enabled (true) unless an override sets it false.
     */
    public static boolean isAiEnabledFor(String entityTypeId) {
        loadIfNeeded();
        if (entityTypeId == null || entityTypeId.isBlank()) {
            return true;
        }
        synchronized (LOCK) {
            if (config == null || !config.enabled) {
                return true;
            }
            Boolean v = config.aiEnabledOverrides.get(entityTypeId);
            return v == null ? true : v;
        }
    }

    public static void setEnabled(boolean enabled) {
        loadIfNeeded();
        synchronized (LOCK) {
            config.enabled = enabled;
        }
        save();
    }

    /**
     * Set override for an entity.
     *
     * @param entityTypeId entity type id like "minecraft:zombie"
     * @param enabledOrNullToClear true/false to set, null to remove override
     */
    public static void setOverride(String entityTypeId, Boolean enabledOrNullToClear) {
        loadIfNeeded();
        if (entityTypeId == null || entityTypeId.isBlank()) {
            return;
        }
        synchronized (LOCK) {
            if (enabledOrNullToClear == null) {
                config.aiEnabledOverrides.remove(entityTypeId);
            } else {
                config.aiEnabledOverrides.put(entityTypeId, enabledOrNullToClear);
            }
        }
        save();
    }
}
