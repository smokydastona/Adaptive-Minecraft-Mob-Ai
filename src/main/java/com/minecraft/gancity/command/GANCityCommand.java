package com.minecraft.gancity.command;

import com.minecraft.gancity.GANCityMod;
import com.minecraft.gancity.ai.MobBehaviorAI;
import com.minecraft.gancity.ai.VillagerDialogueAI;
import com.minecraft.gancity.mca.MCAIntegration;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Commands for testing and managing AI features
 */
public class GANCityCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcaai")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("test")
                .then(Commands.literal("dialogue")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .executes(context -> testDialogue(context, 
                            StringArgumentType.getString(context, "type"))))))
            .then(Commands.literal("info")
                .executes(GANCityCommand::showInfo))
            .then(Commands.literal("stats")
                .executes(GANCityCommand::showStats))
        );
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
        source.sendSuccess(() -> Component.literal("  /mcaai info - Show this information"), false);
        source.sendSuccess(() -> Component.literal("  /mcaai stats - View AI statistics"), false);
        source.sendSuccess(() -> Component.literal("  /mcaai test dialogue <type> - Test dialogue generation"), false);
        
        if (!mcaLoaded) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("§cNote: Install MCA Reborn for full features!§r"), false);
            source.sendSuccess(() -> Component.literal("§eDownload: https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn§r"), false);
        }
        
        return 1;
    }

    private static int showStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MobBehaviorAI behaviorAI = GANCityMod.getMobBehaviorAI();
        
        source.sendSuccess(() -> Component.literal("§b=== AI Statistics ===§r"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§eMob AI Machine Learning:§r"), false);
        
        if (behaviorAI != null) {
            String mlStats = behaviorAI.getMLStats();
            source.sendSuccess(() -> Component.literal("  " + mlStats), false);
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("  Active mob types: 4 (Zombie, Skeleton, Creeper, Spider)"), false);
            source.sendSuccess(() -> Component.literal("  Actions per type: 3-4 tactical behaviors"), false);
            source.sendSuccess(() -> Component.literal("  §aLearning: Progressive difficulty increase over time§r"), false);
        } else {
            source.sendSuccess(() -> Component.literal("  Status: §cDisabled§r"), false);
        }
        
        if (MCAIntegration.isMCALoaded()) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("§eVillager Dialogue:§r"), false);
            source.sendSuccess(() -> Component.literal("  Dialogue templates: 40+"), false);
            source.sendSuccess(() -> Component.literal("  Personality traits: 7"), false);
            source.sendSuccess(() -> Component.literal("  Mood states: 6"), false);
        }
        
        return 1;
    }
}
