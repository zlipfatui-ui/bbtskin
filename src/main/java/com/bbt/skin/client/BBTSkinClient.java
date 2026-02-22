package com.bbt.skin.client;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.client.gui.screen.SkinSelectionScreen;
import com.bbt.skin.client.render.SkinTextureManager;
import com.bbt.skin.client.voice.VoiceStateTracker;
import com.bbt.skin.common.data.SkinData;
import com.bbt.skin.common.data.SkinManager;
import com.bbt.skin.common.network.NetworkHandler;
import com.bbt.skin.common.network.packet.SkinRequestPacket;
import com.bbt.skin.common.network.packet.SkinResetPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Client-side initialization for BBTSkin (Forge 1.20.1)
 * Version 1.2.0: Added Plasmo Voice integration for mouth animation
 */
@Mod.EventBusSubscriber(modid = BBTSkin.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BBTSkinClient {
    
    private static BBTSkinClient instance;
    
    // Keybind for opening skin GUI (default: K)
    public static final KeyMapping OPEN_SKIN_GUI_KEY = new KeyMapping(
            "key.bbtskin.open_gui",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "category.bbtskin.general"
    );
    
    // Managers
    private SkinManager skinManager;
    private SkinTextureManager textureManager;
    
    private BBTSkinClient() {
        this.skinManager = new SkinManager();
        this.textureManager = new SkinTextureManager();
    }
    
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        BBTSkin.LOGGER.info("[BBTSkin v1.3.0-patch] Initializing {} client...", BBTSkin.MOD_NAME);
        
        instance = new BBTSkinClient();
        
        // Register game event handlers
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
        
        // Load local skins
        instance.skinManager.loadLocalSkins();
        
        // Initialize voice state tracker (Plasmo Voice integration)
        VoiceStateTracker.getInstance().init();
        
        BBTSkin.LOGGER.info("[BBTSkin v1.3.0-patch] {} client initialized!", BBTSkin.MOD_NAME);
    }
    
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SKIN_GUI_KEY);
        BBTSkin.LOGGER.info("Registered keybind: {} (default: K)", OPEN_SKIN_GUI_KEY.getName());
    }
    
    public static BBTSkinClient getInstance() {
        return instance;
    }
    
    public SkinManager getSkinManager() {
        return skinManager;
    }
    
    public SkinTextureManager getTextureManager() {
        return textureManager;
    }
    
    // Static methods for network calls
    public static void syncCurrentSkin() {
        if (instance == null) return;
        
        SkinData skin = instance.skinManager.getAppliedSkin();
        if (skin == null) {
            BBTSkin.LOGGER.warn("No skin to sync");
            return;
        }
        
        byte[] imageData = skin.getImageData();
        if (imageData == null) {
            BBTSkin.LOGGER.warn("Skin has no image data");
            return;
        }
        
        try {
            // Use the chunked network method which automatically handles large skins
            NetworkHandler.sendSkinToServer(
                    skin.getId(),
                    imageData,
                    skin.isSlim(),
                    skin.getName(),
                    skin.getWidth(),
                    skin.getHeight()
            );
            BBTSkin.LOGGER.info("Synced skin to server: {} ({} bytes)", skin.getName(), imageData.length);
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to sync skin", e);
        }
    }
    
    public static void sendSkinReset() {
        try {
            NetworkHandler.sendToServer(new SkinResetPacket());
            BBTSkin.LOGGER.info("Sent skin reset to server");
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to send skin reset", e);
        }
    }
    
    public static void requestPlayerSkin(String playerUUID) {
        try {
            NetworkHandler.sendToServer(new SkinRequestPacket(playerUUID));
            BBTSkin.LOGGER.debug("Requested skin for player {}", playerUUID);
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to request player skin", e);
        }
    }
    
    /**
     * Handle skin response from server (for remote players)
     * Called by SkinResponsePacket and SkinResponseChunkPacket handlers
     */
    public void handleSkinResponse(String playerUUID, String skinId, byte[] skinData,
                                   boolean slim, String skinName, int width, int height) {
        if (skinData == null || skinData.length == 0) {
            BBTSkin.LOGGER.debug("Received empty skin for player {}", playerUUID);
            return;
        }
        
        try {
            UUID uuid = UUID.fromString(playerUUID);
            
            // Create skin data object using Builder
            SkinData remoteSkin = new SkinData.Builder()
                    .id(skinId)
                    .name(skinName)
                    .slim(slim)
                    .width(width)
                    .height(height)
                    .imageData(skinData)
                    .build();
            
            // Load texture for this remote player
            textureManager.loadRemoteSkin(uuid, remoteSkin);
            
            BBTSkin.LOGGER.info("Loaded remote skin '{}' for player {} ({} bytes)", 
                    skinName, playerUUID, skinData.length);
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to handle skin response", e);
        }
    }
    
    /**
     * Game event handler for client-side events
     */
    public static class ClientEventHandler {
        
        @SubscribeEvent
        public void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            
            if (OPEN_SKIN_GUI_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new SkinSelectionScreen());
                }
            }
        }
        
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                // Update voice state tracker for mouth animation
                VoiceStateTracker.getInstance().tick();
            }
        }
        
        @SubscribeEvent
        public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity().level().isClientSide()) {
                BBTSkin.LOGGER.info("Joined server, syncing skin data...");
                
                if (instance != null) {
                    instance.skinManager.loadLocalSkins();
                    
                    // Load applied skin texture
                    SkinData appliedSkin = instance.skinManager.getAppliedSkin();
                    if (appliedSkin != null) {
                        instance.textureManager.loadLocalSkin(appliedSkin);
                        // Sync to server
                        syncCurrentSkin();
                    }
                }
            }
        }
        
        @SubscribeEvent
        public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity().level().isClientSide()) {
                BBTSkin.LOGGER.info("Disconnected from server, clearing remote textures...");
                if (instance != null) {
                    instance.textureManager.clearRemoteTextures();
                }
            }
        }
    }
}
