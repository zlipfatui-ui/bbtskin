package com.bbt.skin;

import com.bbt.skin.common.config.BBTSkinConfig;
import com.bbt.skin.common.network.NetworkHandler;
import com.bbt.skin.server.network.ServerSkinHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BBTSkin - Custom Skin Manager for BeforeBedtime Network
 * A high-performance skin management mod with Cloudflare Worker KV integration
 * 
 * @author BeforeBedtime Team
 * @version 1.0.0
 */
@Mod(BBTSkin.MOD_ID)
public class BBTSkin {
    
    public static final String MOD_ID = "bbtskin";
    public static final String MOD_NAME = "BBTSkin";
    public static final String VERSION = "1.0.0";
    
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    
    private static BBTSkin instance;
    
    public BBTSkin() {
        instance = this;
        
        LOGGER.info("Initializing {} v{}", MOD_NAME, VERSION);
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // Register setup event
        modEventBus.addListener(this::commonSetup);
        
        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.register(this);
        
        // Load configuration
        BBTSkinConfig.load();
        
        LOGGER.info("{} constructor completed!", MOD_NAME);
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("{} common setup starting...", MOD_NAME);
        
        // Register network packets
        event.enqueueWork(() -> {
            NetworkHandler.register();
            LOGGER.info("Network packets registered!");
            
            // Initialize API client if configured
            BBTSkinConfig config = BBTSkinConfig.get();
            if (config.isCloudflareEnabled()) {
                ServerSkinHandler.initializeApi(
                        config.getCloudflareWorkerUrl(),
                        config.getCloudflareApiKey()
                );
            }
        });
        
        LOGGER.info("{} common setup completed!", MOD_NAME);
    }
    
    public static BBTSkin getInstance() {
        return instance;
    }
    
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
