package com.minecraft.gancity;

import com.minecraft.gancity.ai.MobBehaviorAI;
import com.minecraft.gancity.ai.VillagerDialogueAI;
import com.minecraft.gancity.command.GANCityCommand;
import com.minecraft.gancity.compat.ModCompatibility;
import com.minecraft.gancity.compat.CuriosIntegration;
import com.minecraft.gancity.compat.FTBTeamsIntegration;
import com.minecraft.gancity.mca.MCAIntegration;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(GANCityMod.MODID)
public class GANCityMod {
    public static final String MODID = "gancity";
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static MobBehaviorAI mobBehaviorAI;
    private static VillagerDialogueAI villagerDialogueAI;

    public GANCityMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("MCA AI Enhanced - Initializing AI systems...");
        
        // Initialize mod compatibility system
        ModCompatibility.init();
        CuriosIntegration.init();
        FTBTeamsIntegration.init();
        
        // Initialize AI systems
        mobBehaviorAI = new MobBehaviorAI();
        villagerDialogueAI = new VillagerDialogueAI();
        
        // Check if MCA Reborn is loaded
        boolean mcaLoaded = ModList.get().isLoaded("mca");
        MCAIntegration.setMCALoaded(mcaLoaded);
        
        if (mcaLoaded) {
            LOGGER.info("MCA AI Enhanced - MCA Reborn detected! Enhanced villager AI enabled.");
        } else {
            LOGGER.warn("MCA AI Enhanced - MCA Reborn not found. Villager dialogue features disabled.");
        }
        
        LOGGER.info("MCA AI Enhanced - Mob behavior AI initialized with {} mob types", 
            mobBehaviorAI != null ? "multiple" : "0");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("MCA AI Enhanced - Server starting with AI enhancements");
    }
    
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("MCA AI Enhanced - Server stopping, saving ML models...");
        if (mobBehaviorAI != null) {
            mobBehaviorAI.saveModel();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        GANCityCommand.register(event.getDispatcher());
        LOGGER.info("MCA AI Enhanced - Commands registered");
    }

    public static MobBehaviorAI getMobBehaviorAI() {
        return mobBehaviorAI;
    }
    
    public static VillagerDialogueAI getVillagerDialogueAI() {
        return villagerDialogueAI;
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
