package com.minecraft.gancity;

import com.minecraft.gancity.ai.MobBehaviorAI;
import com.minecraft.gancity.ai.VillagerDialogueAI;
import com.minecraft.gancity.command.GANCityCommand;
import com.minecraft.gancity.compat.ModCompatibility;
import com.minecraft.gancity.mca.MCAIntegration;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

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
    // Config (minimal TOML parsing; no ForgeConfigSpec yet)
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
    
    // Auto-save tracking (10 minutes = 12000 ticks)
    private static final int AUTO_SAVE_INTERVAL_TICKS = 12000;
    private static int tickCounter = 0;
    private static long lastSaveTime = 0;

    public GANCityMod() {
        System.out.println("=== MCA AI Enhanced: Constructor START ===");
        LOGGER.info("=== MCA AI Enhanced: Constructor START ===");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("=== MCA AI Enhanced: Constructor FINISH ===");
        LOGGER.info("=== MCA AI Enhanced: Constructor FINISH ===");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        System.out.println("=== MCA AI Enhanced: commonSetup START ===");
        LOGGER.info("MCA AI Enhanced - Deferring initialization to avoid classloading deadlock");
        
        event.enqueueWork(() -> {
            try {
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
                java.nio.file.Path configPath = java.nio.file.Paths.get("config", CONFIG_FILE_NAME);
                if (!java.nio.file.Files.exists(configPath)) {
                    LOGGER.info("Config file not found, creating default config...");
                    createDefaultConfigFromResources(configPath);
                }

                java.util.Map<String, String> kv = parseTomlKeyValues(configPath);

                safeMode = parseBoolean(kv, "safeMode", false);
                enableMobAI = parseBoolean(kv, "enableMobAI", true);
                enableVillagerDialogue = parseBoolean(kv, "enableVillagerDialogue", true);
                enableLearning = parseBoolean(kv, "enableLearning", true);
                aiDifficulty = parseFloat(kv, "aiDifficulty", 1.0f);

                enableCrossMobLearning = parseBoolean(kv, "enableCrossMobLearning", true);
                crossMobRewardMultiplier = parseFloat(kv, "crossMobRewardMultiplier", 3.0f);
                enableContextualDifficulty = parseBoolean(kv, "enableContextualDifficulty", true);

                enableFederatedLearning = parseBoolean(kv, "enableFederatedLearning", true);
                cloudApiEndpoint = parseString(kv, "cloudApiEndpoint", DEFAULT_CLOUDFLARE_ENDPOINT);
                if (cloudApiEndpoint == null || cloudApiEndpoint.isEmpty()) {
                    cloudApiEndpoint = DEFAULT_CLOUDFLARE_ENDPOINT;
                }
                cloudApiKey = parseString(kv, "cloudApiKey", "");

                tierProgressionEnabled = parseBoolean(kv, "enableTierProgression", true);
                visualTierIndicators = parseBoolean(kv, "enableVisualTierIndicators", true);
                expRateMultiplier = parseFloat(kv, "experienceRateMultiplier", 1.0f);
                syncTiersWithFederation = parseBoolean(kv, "syncTiersWithFederation", true);

                configLoaded = true;
            } catch (Exception e) {
                // Fail open with safe defaults (ON) so players have zero setup.
                LOGGER.warn("Failed to load config; using built-in defaults: {}", e.getMessage());
                configLoaded = true;
            }
        }
    }

    private static java.util.Map<String, String> parseTomlKeyValues(java.nio.file.Path configPath) throws java.io.IOException {
        java.util.Map<String, String> kv = new java.util.HashMap<>();
        java.util.List<String> lines = java.nio.file.Files.readAllLines(configPath);
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

    private static boolean parseBoolean(java.util.Map<String, String> kv, String key, boolean defaultValue) {
        String value = kv.get(key);
        if (value == null) {
            return defaultValue;
        }
        value = stripQuotes(value).trim();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        return defaultValue;
    }

    private static float parseFloat(java.util.Map<String, String> kv, String key, float defaultValue) {
        String value = kv.get(key);
        if (value == null) {
            return defaultValue;
        }
        value = stripQuotes(value).trim();
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String parseString(java.util.Map<String, String> kv, String key, String defaultValue) {
        String value = kv.get(key);
        if (value == null) {
            return defaultValue;
        }
        value = stripQuotes(value).trim();
        return value;
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
    
    /**
     * Create default config file from resources
     */
    private static void createDefaultConfigFromResources(java.nio.file.Path configPath) throws java.io.IOException {
        // Create config directory if it doesn't exist
        java.nio.file.Files.createDirectories(configPath.getParent());
        
        // Copy from resources to config directory
        try (java.io.InputStream inputStream = GANCityMod.class.getResourceAsStream("/adaptivemobai-common.toml")) {
            if (inputStream != null) {
                java.nio.file.Files.copy(inputStream, configPath);
                LOGGER.info("Created default config at {}", configPath);
            } else {
                LOGGER.error("Could not find default config in resources");
            }
        }
    }
}
