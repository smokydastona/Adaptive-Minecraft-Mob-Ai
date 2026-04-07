package com.minecraft.gancity.command;

import com.minecraft.gancity.GANCityMod;
import com.minecraft.gancity.ai.MobBehaviorAI;
import com.minecraft.gancity.ai.VillagerDialogueAI;
import com.minecraft.gancity.compat.ModCompatibility;
import com.minecraft.gancity.config.PlayerMobLoadoutStore;
import com.minecraft.gancity.mca.MCAIntegration;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Commands for testing and managing AI features
 */
@SuppressWarnings("null")
public class GANCityCommand {

    private static final int MAX_LISTED_MOBS = 15;
    private static final int MAX_LISTED_OPTIONS = 20;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("amai")
            .then(Commands.literal("test")
                .then(Commands.literal("dialogue")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .executes(context -> testDialogue(context, 
                            StringArgumentType.getString(context, "type"))))))
            .then(Commands.literal("info")
                .executes(GANCityCommand::showInfo))
            .then(Commands.literal("stats")
                .executes(GANCityCommand::showStats))
            .then(Commands.literal("status")
                .executes(GANCityCommand::showFederationStatus))
            .then(Commands.literal("compat")
                .executes(GANCityCommand::showCompatibility))
            .then(Commands.literal("loadout")
                .then(Commands.literal("list")
                    .executes(GANCityCommand::loadoutList))
                .then(Commands.literal("list")
                    .then(Commands.argument("mob", StringArgumentType.word())
                        .executes(context -> loadoutListMob(context, StringArgumentType.getString(context, "mob")))))
                .then(Commands.literal("enable")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> loadoutEnable(context, BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("add")
                    .then(Commands.argument("mob", StringArgumentType.word())
                        .then(Commands.argument("item", StringArgumentType.word())
                            .then(Commands.argument("weight", DoubleArgumentType.doubleArg(0.000001))
                                .executes(context -> loadoutAdd(context,
                                    StringArgumentType.getString(context, "mob"),
                                    StringArgumentType.getString(context, "item"),
                                    DoubleArgumentType.getDouble(context, "weight")))))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("mob", StringArgumentType.word())
                        .then(Commands.argument("item", StringArgumentType.word())
                            .executes(context -> loadoutRemove(context,
                                StringArgumentType.getString(context, "mob"),
                                StringArgumentType.getString(context, "item"))))))
                .then(Commands.literal("clear")
                    .then(Commands.argument("mob", StringArgumentType.word())
                        .executes(context -> loadoutClear(context, StringArgumentType.getString(context, "mob")))))
                .then(Commands.literal("reset")
                    .executes(GANCityCommand::loadoutReset))
            )
        );
    }

    private static Player requirePlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return null;
        }
    }

    private static int loadoutList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        PlayerMobLoadoutStore.PlayerLoadout loadout = PlayerMobLoadoutStore.getOrCreate(player.getUUID());
        source.sendSuccess(() -> Component.literal("§b=== Mob Weapon Loadouts (per-player) ===§r"), false);
        source.sendSuccess(() -> Component.literal("Enabled: " + (loadout.enabled ? "§atrue§r" : "§cfalse§r")), false);
        if (loadout.mobs == null || loadout.mobs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No per-mob loadouts set yet. Edit config or use /amai loadout add <mob> <item|none> <weight>.§r"), false);
            return 1;
        }

        int shown = 0;
        for (var entry : loadout.mobs.entrySet()) {
            if (shown >= MAX_LISTED_MOBS) {
                source.sendSuccess(() -> Component.literal("§7... (more mobs omitted)§r"), false);
                break;
            }
            String mobId = entry.getKey();
            PlayerMobLoadoutStore.MobLoadout mob = entry.getValue();
            int count = mob != null && mob.options != null ? mob.options.size() : 0;
            boolean enabled = mob != null && mob.enabled;
            source.sendSuccess(() -> Component.literal(String.format("  §e%s§r enabled=%s options=%d", mobId, enabled, count)), false);
            shown++;
        }
        source.sendSuccess(() -> Component.literal("§7Use /amai loadout list <mob> to see options. 'none' means unarmed.§r"), false);
        return 1;
    }

    private static int loadoutListMob(CommandContext<CommandSourceStack> context, String mobId) {
        CommandSourceStack source = context.getSource();
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        PlayerMobLoadoutStore.PlayerLoadout loadout = PlayerMobLoadoutStore.getOrCreate(player.getUUID());
        if (loadout.mobs == null || loadout.mobs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No loadouts configured."), false);
            return 1;
        }

        String key = mobId == null ? PlayerMobLoadoutStore.DEFAULT_MOB_KEY : mobId;
        PlayerMobLoadoutStore.MobLoadout mob = loadout.mobs.get(key);
        if (mob == null) {
            // Try normalized minecraft prefix if user typed bare.
            mob = loadout.mobs.get("minecraft:" + key);
            key = mob != null ? ("minecraft:" + key) : key;
        }
        if (mob == null) {
            source.sendSuccess(() -> Component.literal("No loadout found for: " + mobId + " (try minecraft:zombie or 'default')"), false);
            return 1;
        }

        final String keyFinal = key;
        final boolean mobEnabled = mob.enabled;
        source.sendSuccess(() -> Component.literal("§b=== Loadout: " + keyFinal + " ===§r"), false);
        source.sendSuccess(() -> Component.literal("Enabled: " + (mobEnabled ? "§atrue§r" : "§cfalse§r")), false);
        if (mob.options == null || mob.options.isEmpty()) {
            source.sendSuccess(() -> Component.literal("(no options)"), false);
            return 1;
        }

        int shown = 0;
        for (PlayerMobLoadoutStore.WeaponOption opt : mob.options) {
            if (shown >= MAX_LISTED_OPTIONS) {
                source.sendSuccess(() -> Component.literal("§7... (more options omitted)§r"), false);
                break;
            }
            if (opt == null) {
                continue;
            }
            source.sendSuccess(() -> Component.literal(String.format("  §f%s§r  weight=%.4f", String.valueOf(opt.item), opt.weight)), false);
            shown++;
        }
        return 1;
    }

    private static int loadoutEnable(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandSourceStack source = context.getSource();
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        PlayerMobLoadoutStore.setEnabled(player.getUUID(), enabled);
        source.sendSuccess(() -> Component.literal("Mob loadout config " + (enabled ? "§aenabled§r" : "§cdisabled§r") + "."), false);
        return 1;
    }

    private static int loadoutAdd(CommandContext<CommandSourceStack> context, String mobId, String itemId, double weight) {
        CommandSourceStack source = context.getSource();
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        boolean ok = PlayerMobLoadoutStore.addOption(player.getUUID(), mobId, itemId, weight);
        if (!ok) {
            source.sendFailure(Component.literal("Could not add option. Examples: minecraft:diamond_sword 5.0 OR none 1.0"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Added/updated §f" + itemId + "§r for §e" + mobId + "§r (weight=" + weight + ")"), false);
        return 1;
    }

    private static int loadoutRemove(CommandContext<CommandSourceStack> context, String mobId, String itemId) {
        CommandSourceStack source = context.getSource();
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        boolean ok = PlayerMobLoadoutStore.removeOption(player.getUUID(), mobId, itemId);
        if (!ok) {
            source.sendFailure(Component.literal("Could not remove option."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Removed §f" + itemId + "§r for §e" + mobId + "§r"), false);
        return 1;
    }

    private static int loadoutClear(CommandContext<CommandSourceStack> context, String mobId) {
        CommandSourceStack source = context.getSource();
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        PlayerMobLoadoutStore.clearOptions(player.getUUID(), mobId);
        source.sendSuccess(() -> Component.literal("Cleared options for §e" + mobId + "§r"), false);
        return 1;
    }

    private static int loadoutReset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        PlayerMobLoadoutStore.reset(player.getUUID());
        source.sendSuccess(() -> Component.literal("Reset your mob loadout config."), false);
        return 1;
    }

    private static int testDialogue(CommandContext<CommandSourceStack> context, String interactionType) {
        CommandSourceStack source = context.getSource();
        
        try {
            if (!MCAIntegration.isMCALoaded()) {
                source.sendFailure(Component.literal("MCA Reborn is not installed!"));
                return 0;
            }
            
            // Generate test dialogue
            VillagerDialogueAI dialogueAI = GANCityMod.getVillagerDialogueAI();
            VillagerDialogueAI.DialogueContext dialogueContext = 
                new VillagerDialogueAI.DialogueContext(interactionType);
            
            dialogueContext.playerName = source.getTextName();
            dialogueContext.relationshipLevel = 50;
            dialogueContext.timeOfDay = "day";
            
            UUID testVillagerId = UUID.randomUUID();
            String dialogue = dialogueAI.generateDialogue(testVillagerId, dialogueContext);
            
            source.sendSuccess(() -> Component.literal("§6Test Villager: §f" + dialogue), false);
            return 1;
            
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error generating dialogue: " + e.getMessage()));
            return 0;
        }
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        boolean mcaLoaded = MCAIntegration.isMCALoaded();
        
        source.sendSuccess(() -> Component.literal("§b=== MCA AI Enhanced ===§r"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§eMob AI Features:§r"), false);
        source.sendSuccess(() -> Component.literal("  ✓ Adaptive combat behavior"), false);
        source.sendSuccess(() -> Component.literal("  ✓ Learning attack patterns"), false);
        source.sendSuccess(() -> Component.literal("  ✓ Contextual decision making"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§eMCA Villager Features:§r"), false);
        source.sendSuccess(() -> Component.literal("  Status: " + 
            (mcaLoaded ? "§aEnabled§r" : "§cDisabled (MCA not found)§r")), false);
        if (mcaLoaded) {
            source.sendSuccess(() -> Component.literal("  ✓ AI-powered dialogue generation"), false);
            source.sendSuccess(() -> Component.literal("  ✓ Evolving personalities"), false);
            source.sendSuccess(() -> Component.literal("  ✓ Context-aware responses"), false);
            source.sendSuccess(() -> Component.literal("  ✓ Mood tracking"), false);
        }
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§eCommands:§r"), false);
        source.sendSuccess(() -> Component.literal("  /amai info - Show mod information"), false);
        source.sendSuccess(() -> Component.literal("  /amai stats - View AI statistics"), false);
        source.sendSuccess(() -> Component.literal("  /amai compat - View mod compatibility report"), false);
        source.sendSuccess(() -> Component.literal("  /amai test dialogue <type> - Test dialogue generation"), false);
        source.sendSuccess(() -> Component.literal("  /amai loadout ... - Configure per-player, per-mob weapon chances (supports 'none' for unarmed)"), false);
        
        if (!mcaLoaded) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("§cNote: Install MCA Reborn for full features!§r"), false);
            source.sendSuccess(() -> Component.literal("§eDownload: https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn§r"), false);
        }
        
        return 1;
    }
    
    private static int showCompatibility(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        String report = ModCompatibility.getCompatibilityReport();
        for (String line : report.split("\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§eRecommended Mods for Enhanced AI:§r"), false);
        source.sendSuccess(() -> Component.literal("  • Curios API - Basic curio-based equipment heuristics"), false);
        source.sendSuccess(() -> Component.literal("  • FTB Teams - Multiplayer team AI"), false);
        source.sendSuccess(() -> Component.literal("  • Epic Fight - Basic integration (mode/stamina/charging)"), false);
        source.sendSuccess(() -> Component.literal("  • Alex's Mobs - Extended mob behavior patterns"), false);
        
        return 1;
    }

    private static int showStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MobBehaviorAI behaviorAI = GANCityMod.getMobBehaviorAI();
        VillagerDialogueAI dialogueAI = GANCityMod.getVillagerDialogueAI();
        
        source.sendSuccess(() -> Component.literal("§b=== AI Statistics ===§r"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§eMob AI Machine Learning:§r"), false);
        
        if (behaviorAI != null) {
            String mlStats = behaviorAI.getMLStats();
            source.sendSuccess(() -> Component.literal("  " + mlStats), false);
            source.sendSuccess(() -> Component.literal(""), false);
            
            // Get per-mob learning statistics
            Map<String, MobBehaviorAI.MobLearningStats> mobStats = behaviorAI.getPerMobStats();
            
            if (mobStats.isEmpty()) {
                source.sendSuccess(() -> Component.literal("  §7No combat data yet - fight some mobs to see AI learning!§r"), false);
            } else {
                source.sendSuccess(() -> Component.literal("  §6Per-Mob Learning Progress:§r"), false);
                
                // Sort by interaction count (most active first)
                mobStats.entrySet().stream()
                    .sorted((e1, e2) -> Integer.compare(e2.getValue().totalInteractions, e1.getValue().totalInteractions))
                    .forEach(entry -> {
                        MobBehaviorAI.MobLearningStats stats = entry.getValue();
                        
                        // Skip mobs with no interactions
                        if (stats.totalInteractions == 0) return;
                        
                        // Mob header with tier
                        String mobName = stats.mobType.substring(0, 1).toUpperCase() + stats.mobType.substring(1);
                        String tierColor = stats.tier.equals("ELITE") ? "§6" : stats.tier.equals("VETERAN") ? "§e" : "§7";
                        
                        source.sendSuccess(() -> Component.literal(String.format(
                            "    §b%s§r [%s%s§r] - %d interactions",
                            mobName, tierColor, stats.tier, stats.totalInteractions
                        )), false);
                        
                        // Top tactic with success rate
                        if (!stats.bestTactic.equals("none")) {
                            String tacticDisplay = stats.bestTactic.replace("_", " ");
                            String successColor = stats.bestSuccessRate >= 0.7f ? "§a" : 
                                                stats.bestSuccessRate >= 0.5f ? "§e" : "§c";
                            
                            source.sendSuccess(() -> Component.literal(String.format(
                                "      Best: §f%s§r %s(%.0f%% success)§r",
                                tacticDisplay, successColor, stats.bestSuccessRate * 100
                            )), false);
                        }
                        
                        // Tier progress
                        source.sendSuccess(() -> Component.literal(String.format(
                            "      Progress: §d%s§r",
                            stats.getTierProgress()
                        )), false);
                        
                        // Show top 3 tactics if more than one learned
                        if (stats.topTactics.size() > 1) {
                            String tacticsStr = stats.topTactics.stream()
                                .limit(3)
                                .map(t -> t.replace("_", " "))
                                .collect(java.util.stream.Collectors.joining("§7, §f"));
                            
                            source.sendSuccess(() -> Component.literal(String.format(
                                "      Known: §f%s§r",
                                tacticsStr
                            )), false);
                        }
                    });
            }
            
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("  Active mob types: 70+ (All vanilla + modded)"), false);
            source.sendSuccess(() -> Component.literal("  §aLearning: Progressive difficulty with 6 ML systems§r"), false);
        } else {
            source.sendSuccess(() -> Component.literal("  Status: §cDisabled§r"), false);
        }
        
        if (MCAIntegration.isMCALoaded() && dialogueAI != null) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("§eVillager Dialogue AI:§r"), false);
            String dialogueStats = dialogueAI.getStats();
            source.sendSuccess(() -> Component.literal("  " + dialogueStats), false);
            source.sendSuccess(() -> Component.literal("  Personality traits: 7"), false);
            source.sendSuccess(() -> Component.literal("  Mood states: 6"), false);
            source.sendSuccess(() -> Component.literal("  §aGPT-style natural conversations enabled§r"), false);
        }
        
        return 1;
    }
    
    private static int showFederationStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MobBehaviorAI behaviorAI = GANCityMod.getMobBehaviorAI();
        
        source.sendSuccess(() -> Component.literal("§b=== Federated Learning Status ===§r"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        
        if (behaviorAI != null && behaviorAI.getFederatedLearning() != null) {
            com.minecraft.gancity.ai.FederatedLearning fl = behaviorAI.getFederatedLearning();
            
            // Check if federation is enabled
            if (!fl.isEnabled()) {
                source.sendSuccess(() -> Component.literal("  §cFederated Learning: DISABLED§r"), false);
                source.sendSuccess(() -> Component.literal("  (Enable in config to join global AI network)"), false);
                return 1;
            }
            
            // Try to get coordinator status
            source.sendSuccess(() -> Component.literal("  §aFederated Learning: ENABLED§r"), false);
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("  §eQuerying coordinator...§r"), false);
            
            // Async check (don't block command)
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> status = fl.getCoordinatorStatus();
                    
                    if (status != null) {
                        int round = (int) status.getOrDefault("round", 0);
                        int contributors = (int) status.getOrDefault("contributors", 0);
                        int modelsInRound = (int) status.getOrDefault("modelsInRound", 0);
                        boolean hasGlobal = (boolean) status.getOrDefault("hasGlobalModel", false);
                        
                        source.sendSuccess(() -> Component.literal(String.format(
                            "  §6Current Round:§r %d", round
                        )), false);
                        
                        source.sendSuccess(() -> Component.literal(String.format(
                            "  §6Contributors:§r %d servers worldwide", contributors
                        )), false);
                        
                        source.sendSuccess(() -> Component.literal(String.format(
                            "  §6Models in Round:§r %d pending aggregation", modelsInRound
                        )), false);
                        
                        String globalStatus = hasGlobal ? "§a✓ Available§r" : "§e⏳ Waiting for first aggregation§r";
                        source.sendSuccess(() -> Component.literal(String.format(
                            "  §6Global Model:§r %s", globalStatus
                        )), false);
                        
                        source.sendSuccess(() -> Component.literal(""), false);
                        source.sendSuccess(() -> Component.literal("  §aConnection: HEALTHY§r"), false);
                        
                    } else {
                        source.sendSuccess(() -> Component.literal("  §cConnection: FAILED§r"), false);
                        source.sendSuccess(() -> Component.literal("  Check logs for details"), false);
                    }
                    
                } catch (Exception e) {
                    source.sendSuccess(() -> Component.literal("  §cError querying coordinator: " + e.getMessage() + "§r"), false);
                }
            });
            
        } else {
            source.sendSuccess(() -> Component.literal("  §cFederated Learning: NOT INITIALIZED§r"), false);
        }
        
        return 1;
    }
}
