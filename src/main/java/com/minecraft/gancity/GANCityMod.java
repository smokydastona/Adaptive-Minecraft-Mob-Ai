package com.minecraft.gancity;

import com.minecraft.gancity.ai.MobBehaviorAI;
import com.minecraft.gancity.ai.VillagerDialogueAI;
import com.minecraft.gancity.client.config.AdaptiveMobAiConfigScreen;
import com.minecraft.gancity.command.GANCityCommand;
import com.minecraft.gancity.compat.ModCompatibility;
import com.minecraft.gancity.config.PlayerMobLoadoutStore;
import com.minecraft.gancity.config.ModdedMobTacticMappingStore;
import com.minecraft.gancity.config.PerMobAiDefaultsStore;
import com.minecraft.gancity.mca.MCAIntegration;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(GANCityMod.MODID)
@Mod.EventBusSubscriber(modid = GANCityMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
@SuppressWarnings({"removal", "null"})
public class GANCityMod {
    public static final String MODID = "adaptivemobai";
    public static final Logger LOGGER = LogUtils.getLogger();  // Changed to public for mixin access
    
    // DIAGNOSTIC: Static initializer runs FIRST - if this doesn't log, class loading itself failed
    static {
        System.out.println("=== MCA AI Enhanced: Static initialization START ===");
        System.out.println("=== If you see this but no 'FINISH', class loading failed ===");
        LOGGER.info("=== MCA AI Enhanced: Static initialization START ===");
    }
    
    private static MobBehaviorAI mobBehaviorAI;
    private static VillagerDialogueAI villagerDialogueAI;
    private static boolean federationInitialized = false;

    // =====================================================
    // Config (ForgeConfigSpec so it appears in Forge's mod config UI)
    // =====================================================
    private static final String CONFIG_FILE_NAME = "adaptivemobai-common.toml";
    private static final String DEFAULT_CLOUDFLARE_ENDPOINT = "https://mca-ai-tactics-api.mc-ai-datcol.workers.dev";

    private static volatile boolean configLoaded = false;
    private static volatile boolean safeMode = false;
    private static volatile boolean enableMobAI = true;
    private static volatile boolean enableVillagerDialogue = true;
    private static volatile boolean enableLearning = true;
    private static volatile float aiDifficulty = 1.0f;

    private static volatile boolean enableCrossMobLearning = true;
    private static volatile float crossMobRewardMultiplier = 3.0f;
    private static volatile boolean enableContextualDifficulty = true;

    private static volatile boolean enableFederatedLearning = true;
    private static volatile String cloudApiEndpoint = DEFAULT_CLOUDFLARE_ENDPOINT;
    private static volatile String cloudApiKey = "";

    private static volatile boolean tierProgressionEnabled = true;
    private static volatile boolean visualTierIndicators = true;
    private static volatile float expRateMultiplier = 1.0f;
    private static volatile boolean syncTiersWithFederation = true;

    // Global (server-wide) mob weapon loadouts from config
    // Format: mobTypeId -> up to 5 weapon item ids (including "none")
    private static volatile Map<String, List<String>> globalMobWeaponLoadouts = Map.of();

    // Global arrows for bow/crossbow weapons
    private static volatile String defaultBowArrowItemId = "minecraft:arrow";
    private static volatile Map<String, String> bowArrowOverrides = Map.of();

    private static final CommonConfig COMMON;
    private static final ForgeConfigSpec COMMON_SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new CommonConfig(builder);
        COMMON_SPEC = builder.build();
    }
    
    // Auto-save tracking (10 minutes = 12000 ticks)
    private static final int AUTO_SAVE_INTERVAL_TICKS = 12000;
    private static int tickCounter = 0;
    private static long lastSaveTime = 0;

    public GANCityMod() {
        System.out.println("=== MCA AI Enhanced: Constructor START ===");
        LOGGER.info("=== MCA AI Enhanced: Constructor START ===");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        // Register Forge config so it shows up in the Mods menu
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, CONFIG_FILE_NAME);

        // Custom client config UI (searchable selectors / "dropdown"-style pickers)
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientOnly::registerConfigScreen);

        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("=== MCA AI Enhanced: Constructor FINISH ===");
        LOGGER.info("=== MCA AI Enhanced: Constructor FINISH ===");
    }

    private static final class ClientOnly {
        private static void registerConfigScreen() {
            ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                    (mc, parent) -> new AdaptiveMobAiConfigScreen(parent)
                )
            );
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        System.out.println("=== MCA AI Enhanced: commonSetup START ===");
        LOGGER.info("MCA AI Enhanced - Deferring initialization to avoid classloading deadlock");
        
        event.enqueueWork(() -> {
            try {
                // Ensure JSON config files exist so users can edit immediately.
                ensureDefaultConfigFilesExist();
                LOGGER.info("MCA AI Enhanced - Initializing AI systems (SERVER-ONLY)...");
                
                // Configure DJL cache (safe - just system properties, no classloading)
                String gameDir = System.getProperty("user.dir");
                String djlCachePath = gameDir + "/libraries/ai.djl";
                System.setProperty("DJL_CACHE_DIR", djlCachePath);
                System.setProperty("ai.djl.offline", "false");
                LOGGER.info("DJL cache configured: {}", djlCachePath);
                
                // Now safe to check mods - ModList is initialized
                ModCompatibility.init();
                
                // Check MCA
                boolean mcaLoaded = ModList.get().isLoaded("mca");
                MCAIntegration.setMCALoaded(mcaLoaded);
                
                if (mcaLoaded) {
                    LOGGER.info("MCA AI Enhanced - MCA Reborn detected! Enhanced villager AI enabled.");
                } else {
                    LOGGER.warn("MCA AI Enhanced - MCA Reborn not found. Villager dialogue features disabled.");
                }
                
                LOGGER.info("MCA AI Enhanced - Initialization complete");
            } catch (Exception e) {
                LOGGER.error("Failed to initialize MCA AI Enhanced: {}", e.getMessage(), e);
            }
        });
    }

    private static void ensureDefaultConfigFilesExist() {
        try {
            // The Forge config system will create the TOML automatically.
            // We only need to ensure our JSON files exist.
            PlayerMobLoadoutStore.ensureFileExists();
            ModdedMobTacticMappingStore.ensureFileExists();
            PerMobAiDefaultsStore.ensureFileExists();
        } catch (Exception e) {
            LOGGER.warn("Failed to ensure default config files exist: {}", e.toString());
        }
    }

    private static void applyForgeConfig() {
        safeMode = COMMON.safeMode.get();
        enableMobAI = COMMON.enableMobAI.get();
        enableVillagerDialogue = COMMON.enableVillagerDialogue.get();
        enableLearning = COMMON.enableLearning.get();
        aiDifficulty = COMMON.aiDifficulty.get().floatValue();

        enableCrossMobLearning = COMMON.enableCrossMobLearning.get();
        crossMobRewardMultiplier = COMMON.crossMobRewardMultiplier.get().floatValue();
        enableContextualDifficulty = COMMON.enableContextualDifficulty.get();

        enableFederatedLearning = COMMON.enableFederatedLearning.get();
        cloudApiEndpoint = COMMON.cloudApiEndpoint.get();
        if (cloudApiEndpoint == null || cloudApiEndpoint.isEmpty()) {
            cloudApiEndpoint = DEFAULT_CLOUDFLARE_ENDPOINT;
        }
        cloudApiKey = COMMON.cloudApiKey.get();

        tierProgressionEnabled = COMMON.enableTierProgression.get();
        visualTierIndicators = COMMON.enableVisualTierIndicators.get();
        expRateMultiplier = COMMON.experienceRateMultiplier.get().floatValue();
        syncTiersWithFederation = COMMON.syncTiersWithFederation.get();

        // Loadouts
        globalMobWeaponLoadouts = parseMobWeaponLoadouts(COMMON.mobWeaponLoadouts.get(), 5);
        defaultBowArrowItemId = normalizeItemId(COMMON.defaultBowArrowItem.get(), "minecraft:arrow", true);
        bowArrowOverrides = parseKeyValueMap(COMMON.mobBowArrowOverrides.get());
    }

    public static ItemStack chooseConfiguredWeaponForMob(String mobTypeId, Random random) {
        loadConfigIfNeeded();
        if (mobTypeId == null || mobTypeId.isBlank()) {
            return null;
        }

        List<String> options = globalMobWeaponLoadouts.get(mobTypeId);
        if (options == null || options.isEmpty()) {
            return null;
        }

        String selected = options.get(random.nextInt(options.size()));
        if (selected == null) {
            return null;
        }

        selected = selected.trim();
        if (selected.isEmpty()) {
            return null;
        }

        if (selected.equalsIgnoreCase("none")) {
            return ItemStack.EMPTY;
        }

        ResourceLocation id = safeResourceLocation(selected);
        if (id == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return null;
        }
        return new ItemStack(item);
    }

    public static ItemStack getConfiguredArrowStackForMob(String mobTypeId) {
        loadConfigIfNeeded();

        String arrowId = null;
        if (mobTypeId != null && !mobTypeId.isBlank()) {
            arrowId = bowArrowOverrides.get(mobTypeId);
        }
        if (arrowId == null || arrowId.isBlank()) {
            arrowId = defaultBowArrowItemId;
        }

        arrowId = normalizeItemId(arrowId, "minecraft:arrow", true);
        if (arrowId == null || arrowId.isBlank() || arrowId.equalsIgnoreCase("none")) {
            return ItemStack.EMPTY;
        }

        ResourceLocation id = safeResourceLocation(arrowId);
        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item, 64);
    }

    /**
     * Reload only the loadout-related config keys from the TOML on disk and apply them immediately.
     *
     * This exists because our custom in-game UI writes the TOML directly; Forge's config system
     * won't always re-broadcast a reload event while the game is running.
     */
    public static void reloadLoadoutsFromDisk() {
        // Ensure baseline config is loaded so we don't later overwrite these values.
        loadConfigIfNeeded();

        try {
            Path configPath = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
            if (!Files.exists(configPath)) {
                return;
            }

            Map<String, String> kv = parseTomlKeyValues(configPath);
            globalMobWeaponLoadouts = parseMobWeaponLoadouts(parseTomlStringListValue(kv.get("mobWeaponLoadouts")), 5);
            defaultBowArrowItemId = normalizeItemId(stripQuotes(kv.getOrDefault("defaultBowArrowItem", "\"minecraft:arrow\"")), "minecraft:arrow", true);
            bowArrowOverrides = parseKeyValueMap(parseTomlStringListValue(kv.get("mobBowArrowOverrides")));

            // Also update the ForgeConfigSpec values (so a later applyForgeConfig() stays consistent)
            List<String> weaponEntries = globalMobWeaponLoadouts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                .toList();
            COMMON.mobWeaponLoadouts.set(weaponEntries);
            COMMON.defaultBowArrowItem.set(defaultBowArrowItemId);
            List<String> arrowEntries = bowArrowOverrides.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();
            COMMON.mobBowArrowOverrides.set(arrowEntries);
        } catch (Exception e) {
            LOGGER.warn("Failed to reload loadouts from disk: {}", e.toString());
        }
    }

    private static Map<String, String> parseTomlKeyValues(Path path) throws java.io.IOException {
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
        return kv;
    }

    private static List<String> parseTomlStringListValue(String raw) {
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

    private static Map<String, List<String>> parseMobWeaponLoadouts(List<? extends String> rawEntries, int maxWeaponsPerMob) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> result = new HashMap<>();
        for (String entry : rawEntries) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int eq = trimmed.indexOf('=');
            if (eq <= 0 || eq >= trimmed.length() - 1) {
                continue;
            }

            String mobId = trimmed.substring(0, eq).trim();
            String weaponsCsv = trimmed.substring(eq + 1).trim();
            if (mobId.isEmpty() || weaponsCsv.isEmpty()) {
                continue;
            }

            String[] parts = weaponsCsv.split(",");
            List<String> weapons = new ArrayList<>();
            for (String part : parts) {
                if (weapons.size() >= maxWeaponsPerMob) {
                    break;
                }
                if (part == null) {
                    continue;
                }
                String weaponId = part.trim();
                if (weaponId.isEmpty()) {
                    continue;
                }

                // Allow explicit unarmed option
                if (weaponId.equalsIgnoreCase("none")) {
                    weapons.add("none");
                    continue;
                }

                String normalized = normalizeItemId(weaponId, null, false);
                if (normalized != null) {
                    weapons.add(normalized);
                }
            }

            if (!weapons.isEmpty()) {
                result.put(mobId, Collections.unmodifiableList(weapons));
            }
        }

        return result.isEmpty() ? Map.of() : Collections.unmodifiableMap(result);
    }

    private static Map<String, String> parseKeyValueMap(List<? extends String> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new HashMap<>();
        for (String entry : rawEntries) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int eq = trimmed.indexOf('=');
            if (eq <= 0 || eq >= trimmed.length() - 1) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }

            String normalizedValue = normalizeItemId(value, null, true);
            if (normalizedValue != null) {
                result.put(key, normalizedValue);
            }
        }

        return result.isEmpty() ? Map.of() : Collections.unmodifiableMap(result);
    }

    private static String normalizeItemId(String raw, String fallback, boolean allowNone) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return fallback;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        if (allowNone && lower.equals("none")) {
            return "none";
        }

        // Permit bare ids like "arrow" as shorthand for "minecraft:arrow"
        if (!lower.contains(":")) {
            lower = "minecraft:" + lower;
        }

        // Validate
        return safeResourceLocation(lower) != null ? lower : fallback;
    }

    private static ResourceLocation safeResourceLocation(String raw) {
        try {
            return new ResourceLocation(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Mod.EventBusSubscriber(modid = GANCityMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModConfigEvents {
        @SubscribeEvent
        public static void onConfigLoad(ModConfigEvent.Loading event) {
            if (event.getConfig().getSpec() == COMMON_SPEC) {
                applyForgeConfig();
                configLoaded = true;
            }
        }

        @SubscribeEvent
        public static void onConfigReload(ModConfigEvent.Reloading event) {
            if (event.getConfig().getSpec() == COMMON_SPEC) {
                applyForgeConfig();
                configLoaded = true;
            }
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("MCA AI Enhanced - Server starting with AI enhancements");
        
        // Initialize federated learning when server starts (works for dedicated servers)
        initFederationIfNeeded();
    }
    
    /**
     * Lazy initialization of federation - can be called from anywhere
     * Safe to call multiple times (idempotent)
     */
    public static void initFederationIfNeeded() {
        if (!federationInitialized) {
            federationInitialized = true;
            try {
                initializeFederatedLearning();
            } catch (Exception e) {
                LOGGER.error("Failed to initialize federation, continuing without it: {}", e.getMessage());
            }
        }
    }
    
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("MCA AI Enhanced - Server stopping, saving ML models...");
        
        if (mobBehaviorAI != null) {
            mobBehaviorAI.saveModel();
        }
    }
    
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        try {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            
            tickCounter++;
            
            // Auto-save every 10 minutes (12000 ticks)
            if (tickCounter >= AUTO_SAVE_INTERVAL_TICKS) {
                tickCounter = 0;
                performAutoSave();
            }
        } catch (Exception e) {
            LOGGER.error("Exception in server tick: {}", e.getMessage());
        }
    }
    
    private void performAutoSave() {
        if (mobBehaviorAI == null) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastSave = (currentTime - lastSaveTime) / 1000; // seconds
        
        LOGGER.info("═══════════════════════════════════════════════════════");
        LOGGER.info("[AUTO-SAVE] Starting periodic save (last save: {}s ago)", timeSinceLastSave);
        LOGGER.info("═══════════════════════════════════════════════════════");
        
        try {
            // 1. Save models locally
            LOGGER.info("[AUTO-SAVE] Step 1/2: Saving ML models locally...");
            mobBehaviorAI.saveModel();
            LOGGER.info("[AUTO-SAVE] ✓ Local models saved");
            
            // 2. Sync with Cloudflare (upload + download)
            LOGGER.info("[AUTO-SAVE] Step 2/2: Syncing with Cloudflare...");
            mobBehaviorAI.syncWithCloudflare();
            LOGGER.info("[AUTO-SAVE] ✓ Cloudflare sync completed");
            
            lastSaveTime = currentTime;
            LOGGER.info("═══════════════════════════════════════════════════════");
            LOGGER.info("[AUTO-SAVE] ✓ All operations completed successfully!");
            LOGGER.info("═══════════════════════════════════════════════════════");
        } catch (Exception e) {
            LOGGER.error("═══════════════════════════════════════════════════════");
            LOGGER.error("[AUTO-SAVE] ✗ Failed: {}", e.getMessage());
            LOGGER.error("═══════════════════════════════════════════════════════");
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        GANCityCommand.register(event.getDispatcher());
        LOGGER.info("MCA AI Enhanced - Commands registered");
    }

    public static MobBehaviorAI getMobBehaviorAI() {
        loadConfigIfNeeded();

        // SAFE MODE / DISABLE: Skip AI initialization entirely
        if (safeMode || !enableMobAI) {
            LOGGER.warn("⚠️ SAFE MODE ENABLED - All ML/AI features disabled");
            return null;  // Mixin will skip AI enhancement if null
        }
        
        if (mobBehaviorAI == null) {
            synchronized (GANCityMod.class) {
                if (mobBehaviorAI == null) {
                    LOGGER.info("Lazy-initializing MobBehaviorAI...");
                    mobBehaviorAI = new MobBehaviorAI();

                    // Apply config (defaults are ON; user can disable in config)
                    try {
                        mobBehaviorAI.setDifficultyMultiplier(aiDifficulty);
                        mobBehaviorAI.setLearningEnabled(enableLearning);
                    } catch (Exception e) {
                        LOGGER.warn("Could not apply core AI config: {}", e.getMessage());
                    }
                    
                    // Load cross-mob learning configuration (default enabled)
                    try {
                        mobBehaviorAI.setCrossMobLearning(enableCrossMobLearning, crossMobRewardMultiplier);
                    } catch (Exception e) {
                        LOGGER.warn("Could not enable cross-mob learning: {}", e.getMessage());
                    }
                    
                    // Load contextual difficulty configuration (default enabled)
                    try {
                        mobBehaviorAI.setContextualDifficulty(enableContextualDifficulty);
                    } catch (Exception e) {
                        LOGGER.warn("Could not enable contextual difficulty: {}", e.getMessage());
                    }
                }
            }
        }
        return mobBehaviorAI;
    }
    
    public static VillagerDialogueAI getVillagerDialogueAI() {
        loadConfigIfNeeded();

        if (safeMode || !enableVillagerDialogue) {
            return null;
        }
        if (villagerDialogueAI == null) {
            synchronized (GANCityMod.class) {
                if (villagerDialogueAI == null) {
                    LOGGER.info("Lazy-initializing VillagerDialogueAI...");
                    villagerDialogueAI = new VillagerDialogueAI();
                }
            }
        }
        return villagerDialogueAI;
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
    
    /**
     * Initialize federated learning from config
     */
    private static void initializeFederatedLearning() {
        try {
            loadConfigIfNeeded();

            if (safeMode || !enableMobAI) {
                LOGGER.info("Federation skipped (safe mode / mob AI disabled)");
                return;
            }

            MobBehaviorAI ai = getMobBehaviorAI();
            if (ai == null) {
                LOGGER.info("Federation skipped (AI not initialized)");
                return;
            }

            if (enableFederatedLearning && cloudApiEndpoint != null && !cloudApiEndpoint.isEmpty()) {
                LOGGER.info("Enabling federated learning (Cloudflare only)...");
                LOGGER.info("  Cloud API: {}", cloudApiEndpoint);

                ai.enableFederatedLearning(null, cloudApiEndpoint, cloudApiKey == null || cloudApiKey.isEmpty() ? null : cloudApiKey);

                LOGGER.info("Testing Cloudflare Worker connection...");
                boolean connected = ai.testCloudflareConnection();
                if (connected) {
                    LOGGER.info("✓ Cloudflare Worker connected successfully!");
                } else {
                    LOGGER.warn("⚠ Cloudflare Worker connection failed - running in offline mode");
                }
            } else {
                LOGGER.info("Federated learning disabled in config");
            }

            // Configure HNN-inspired tier progression system
            LOGGER.info("Configuring AI tier progression system...");
            ai.setTierSystemEnabled(tierProgressionEnabled);
            ai.setVisualTierIndicators(visualTierIndicators);

            if (tierProgressionEnabled) {
                LOGGER.info("✓ AI Tier Progression ENABLED");
                LOGGER.info("  Visual Indicators: {}", visualTierIndicators ? "ON" : "OFF");
                LOGGER.info("  Experience Rate: {}x", expRateMultiplier);
                LOGGER.info("  Federation Sync: {}", syncTiersWithFederation ? "ON" : "OFF");
                LOGGER.info("  Tiers: UNTRAINED → LEARNING → TRAINED → EXPERT → MASTER");
            } else {
                LOGGER.info("✗ AI Tier Progression DISABLED - All mobs use baseline difficulty");
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to initialize federated learning: {}", e.getMessage());
        }
    }

    private static void loadConfigIfNeeded() {
        if (configLoaded) {
            return;
        }
        synchronized (GANCityMod.class) {
            if (configLoaded) {
                return;
            }
            try {
                applyForgeConfig();
                configLoaded = true;
            } catch (Exception e) {
                // Fail open with safe defaults (ON) so players have zero setup.
                LOGGER.warn("Failed to load config; using built-in defaults: {}", e.getMessage());
                configLoaded = true;
            }
        }
    }

    private static final class CommonConfig {
        final ForgeConfigSpec.BooleanValue safeMode;
        final ForgeConfigSpec.BooleanValue enableMobAI;
        final ForgeConfigSpec.BooleanValue enableVillagerDialogue;
        final ForgeConfigSpec.BooleanValue enableLearning;
        final ForgeConfigSpec.DoubleValue aiDifficulty;

        final ForgeConfigSpec.BooleanValue enableCrossMobLearning;
        final ForgeConfigSpec.DoubleValue crossMobRewardMultiplier;
        final ForgeConfigSpec.BooleanValue enableContextualDifficulty;

        final ForgeConfigSpec.BooleanValue enableFederatedLearning;
        final ForgeConfigSpec.ConfigValue<String> cloudApiEndpoint;
        final ForgeConfigSpec.ConfigValue<String> cloudApiKey;

        final ForgeConfigSpec.BooleanValue enableTierProgression;
        final ForgeConfigSpec.BooleanValue enableVisualTierIndicators;
        final ForgeConfigSpec.DoubleValue experienceRateMultiplier;
        final ForgeConfigSpec.BooleanValue syncTiersWithFederation;

        final ForgeConfigSpec.ConfigValue<List<? extends String>> mobWeaponLoadouts;
        final ForgeConfigSpec.ConfigValue<String> defaultBowArrowItem;
        final ForgeConfigSpec.ConfigValue<List<? extends String>> mobBowArrowOverrides;

        CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            safeMode = builder.comment("Disable all ML/AI features entirely (emergency fallback)")
                .define("safeMode", false);
            enableMobAI = builder.comment("Enable AI-enhanced mob behavior")
                .define("enableMobAI", true);
            enableVillagerDialogue = builder.comment("Enable AI-powered villager dialogue (requires MCA Reborn)")
                .define("enableVillagerDialogue", true);
            enableLearning = builder.comment("Allow mobs to learn from combat outcomes")
                .define("enableLearning", true);
            aiDifficulty = builder.comment("Mob AI difficulty multiplier")
                .defineInRange("aiDifficulty", 1.0, 0.5, 3.0);
            builder.pop();

            builder.push("advanced");
            enableCrossMobLearning = builder.comment("Enable cross-mob emergent learning")
                .define("enableCrossMobLearning", true);
            crossMobRewardMultiplier = builder.comment("Reward multiplier for borrowed tactics")
                .defineInRange("crossMobRewardMultiplier", 3.0, 1.0, 10.0);
            enableContextualDifficulty = builder.comment("Enable contextual difficulty")
                .define("enableContextualDifficulty", true);
            builder.pop();

            builder.push("federated_learning");
            enableFederatedLearning = builder.comment("Enable federated learning")
                .define("enableFederatedLearning", true);
            cloudApiEndpoint = builder.comment("Cloudflare Worker API endpoint")
                .define("cloudApiEndpoint", DEFAULT_CLOUDFLARE_ENDPOINT);
            cloudApiKey = builder.comment("Cloud API key for authentication")
                .define("cloudApiKey", "");
            builder.pop();

            builder.push("tier_progression");
            enableTierProgression = builder.comment("Enable tier progression")
                .define("enableTierProgression", true);
            enableVisualTierIndicators = builder.comment("Enable visual tier indicators")
                .define("enableVisualTierIndicators", true);
            experienceRateMultiplier = builder.comment("Experience rate multiplier")
                .defineInRange("experienceRateMultiplier", 1.0, 0.1, 5.0);
            syncTiersWithFederation = builder.comment("Sync tier data with federation")
                .define("syncTiersWithFederation", true);
            builder.pop();

            builder.push("loadouts");
            mobWeaponLoadouts = builder
                .comment(
                    "Global per-mob weapon loadouts (server-wide).",
                    "Each entry is: <mobId>=<weapon1>,<weapon2>,<weapon3>,<weapon4>,<weapon5>",
                    "Use 'none' as a weapon to allow unarmed spawns.",
                    "Example: minecraft:zombie=minecraft:iron_sword,minecraft:stone_sword,none",
                    "If a player-specific loadout exists, it overrides this."
                )
                .defineListAllowEmpty("mobWeaponLoadouts", List.of(), o -> o instanceof String);

            builder.push("bows");
            defaultBowArrowItem = builder
                .comment("Default arrow item to give mobs when they spawn with a bow/crossbow")
                .define("defaultBowArrowItem", "minecraft:arrow");
            mobBowArrowOverrides = builder
                .comment(
                    "Per-mob arrow override when spawning with a bow/crossbow.",
                    "Each entry is: <mobId>=<arrowItemId>",
                    "Example: minecraft:skeleton=minecraft:spectral_arrow"
                )
                .defineListAllowEmpty("mobBowArrowOverrides", List.of(), o -> o instanceof String);
            builder.pop();

            builder.pop();
        }
    }
}
