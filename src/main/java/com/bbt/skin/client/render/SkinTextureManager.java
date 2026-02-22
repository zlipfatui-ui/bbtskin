package com.bbt.skin.client.render;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.client.voice.VoiceStateTracker;
import com.bbt.skin.common.data.SkinData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages skin textures for rendering (Forge)
 * Handles loading, caching, and cleanup of skin textures.
 * Supports dual textures for voice chat (mouth open/closed).
 */
public class SkinTextureManager {
    
    // Cache for player skin textures (UUID -> texture identifier)
    private final Map<UUID, ResourceLocation> skinTextureCache = new ConcurrentHashMap<>();
    private final Map<UUID, ResourceLocation> mouthOpenTextureCache = new ConcurrentHashMap<>();
    
    // Map of dynamic textures (for cleanup)
    private final Map<UUID, DynamicTexture> dynamicTextures = new ConcurrentHashMap<>();
    private final Map<UUID, DynamicTexture> mouthOpenDynamicTextures = new ConcurrentHashMap<>();
    
    // Track which players have voice textures
    private final Map<UUID, Boolean> hasVoiceTexture = new ConcurrentHashMap<>();
    
    // Local player's textures
    @Nullable private ResourceLocation localSkinTexture;
    @Nullable private ResourceLocation localMouthOpenTexture;
    @Nullable private DynamicTexture localDynamicTexture;
    @Nullable private DynamicTexture localMouthOpenDynamicTexture;
    private boolean localHasVoiceTexture = false;
    
    /**
     * Load a skin texture for a remote player (supports voice texture)
     */
    public void loadRemoteSkin(UUID playerUUID, SkinData skinData) {
        if (skinData.getImageData() == null) {
            BBTSkin.LOGGER.warn("Cannot load skin without image data");
            return;
        }
        
        try {
            unloadRemoteSkin(playerUUID);
            
            // Load main texture
            NativeImage image = NativeImage.read(new ByteArrayInputStream(skinData.getImageData()));
            DynamicTexture texture = new DynamicTexture(image);
            
            String texturePath = "bbtskin/player/" + playerUUID.toString().replace("-", "");
            ResourceLocation textureId = Minecraft.getInstance().getTextureManager()
                    .register(texturePath, texture);
            
            skinTextureCache.put(playerUUID, textureId);
            dynamicTextures.put(playerUUID, texture);
            
            // Load mouth-open texture if available
            if (skinData.hasVoiceTexture() && skinData.getMouthOpenData() != null) {
                NativeImage mouthImage = NativeImage.read(new ByteArrayInputStream(skinData.getMouthOpenData()));
                DynamicTexture mouthTexture = new DynamicTexture(mouthImage);
                
                String mouthPath = "bbtskin/player/" + playerUUID.toString().replace("-", "") + "_mouth";
                ResourceLocation mouthId = Minecraft.getInstance().getTextureManager()
                        .register(mouthPath, mouthTexture);
                
                mouthOpenTextureCache.put(playerUUID, mouthId);
                mouthOpenDynamicTextures.put(playerUUID, mouthTexture);
                hasVoiceTexture.put(playerUUID, true);
                
                BBTSkin.LOGGER.debug("Loaded voice texture for player {}", playerUUID);
            } else {
                hasVoiceTexture.put(playerUUID, false);
            }
            
            BBTSkin.LOGGER.debug("Loaded remote skin texture for player {}", playerUUID);
            
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to load remote skin texture", e);
        }
    }
    
    /**
     * Unload a remote player's skin texture
     */
    public void unloadRemoteSkin(UUID playerUUID) {
        skinTextureCache.remove(playerUUID);
        mouthOpenTextureCache.remove(playerUUID);
        hasVoiceTexture.remove(playerUUID);
        
        DynamicTexture texture = dynamicTextures.remove(playerUUID);
        if (texture != null) texture.close();
        
        DynamicTexture mouthTexture = mouthOpenDynamicTextures.remove(playerUUID);
        if (mouthTexture != null) mouthTexture.close();
    }
    
    /**
     * Get the appropriate texture for a player based on talking state
     */
    @Nullable
    public ResourceLocation getRemoteSkinTexture(UUID playerUUID, Player player) {
        // Check if player has voice texture and is talking
        if (hasVoiceTexture.getOrDefault(playerUUID, false)) {
            if (VoiceStateTracker.getInstance().shouldShowMouthOpen(player)) {
                ResourceLocation mouthTexture = mouthOpenTextureCache.get(playerUUID);
                if (mouthTexture != null) return mouthTexture;
            }
        }
        return skinTextureCache.get(playerUUID);
    }
    
    /**
     * Get the texture identifier for a remote player's skin (no voice check)
     */
    @Nullable
    public ResourceLocation getRemoteSkinTexture(UUID playerUUID) {
        return skinTextureCache.get(playerUUID);
    }
    
    /**
     * Check if we have a custom skin for a player
     */
    public boolean hasCustomSkin(UUID playerUUID) {
        return skinTextureCache.containsKey(playerUUID);
    }
    
    /**
     * Check if player has voice texture
     */
    public boolean playerHasVoiceTexture(UUID playerUUID) {
        return hasVoiceTexture.getOrDefault(playerUUID, false);
    }
    
    /**
     * Load the local player's skin texture (with voice support)
     */
    public void loadLocalSkin(SkinData skinData) {
        if (skinData.getImageData() == null) {
            BBTSkin.LOGGER.warn("Cannot load local skin without image data");
            return;
        }
        
        try {
            unloadLocalSkin();
            
            // Load main texture
            NativeImage image = NativeImage.read(new ByteArrayInputStream(skinData.getImageData()));
            localDynamicTexture = new DynamicTexture(image);
            localSkinTexture = Minecraft.getInstance().getTextureManager()
                    .register("bbtskin/local", localDynamicTexture);
            
            // Load mouth-open texture if available
            if (skinData.hasVoiceTexture() && skinData.getMouthOpenData() != null) {
                NativeImage mouthImage = NativeImage.read(new ByteArrayInputStream(skinData.getMouthOpenData()));
                localMouthOpenDynamicTexture = new DynamicTexture(mouthImage);
                localMouthOpenTexture = Minecraft.getInstance().getTextureManager()
                        .register("bbtskin/local_mouth", localMouthOpenDynamicTexture);
                localHasVoiceTexture = true;
                
                BBTSkin.LOGGER.info("Loaded local skin with voice texture");
            } else {
                localHasVoiceTexture = false;
                BBTSkin.LOGGER.info("Loaded local skin texture");
            }
            
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to load local skin texture", e);
        }
    }
    
    /**
     * Unload the local player's skin texture
     */
    public void unloadLocalSkin() {
        localSkinTexture = null;
        localMouthOpenTexture = null;
        localHasVoiceTexture = false;
        
        if (localDynamicTexture != null) {
            localDynamicTexture.close();
            localDynamicTexture = null;
        }
        if (localMouthOpenDynamicTexture != null) {
            localMouthOpenDynamicTexture.close();
            localMouthOpenDynamicTexture = null;
        }
    }
    
    /**
     * Get the local player's skin texture (with voice state check)
     */
    @Nullable
    public ResourceLocation getLocalSkinTexture() {
        if (localHasVoiceTexture && VoiceStateTracker.getInstance().shouldLocalPlayerShowMouthOpen()) {
            if (localMouthOpenTexture != null) return localMouthOpenTexture;
        }
        return localSkinTexture;
    }
    
    /**
     * Get local texture without voice check
     */
    @Nullable
    public ResourceLocation getLocalSkinTextureBase() {
        return localSkinTexture;
    }
    
    /**
     * Check if local player has a custom skin loaded
     */
    public boolean hasLocalSkin() {
        return localSkinTexture != null;
    }
    
    /**
     * Check if local player has voice texture
     */
    public boolean localHasVoiceTexture() {
        return localHasVoiceTexture;
    }
    
    /**
     * Clear all remote skin textures
     */
    public void clearRemoteTextures() {
        for (DynamicTexture texture : dynamicTextures.values()) {
            texture.close();
        }
        for (DynamicTexture texture : mouthOpenDynamicTextures.values()) {
            texture.close();
        }
        dynamicTextures.clear();
        mouthOpenDynamicTextures.clear();
        skinTextureCache.clear();
        mouthOpenTextureCache.clear();
        hasVoiceTexture.clear();
        
        BBTSkin.LOGGER.info("Cleared all remote skin textures");
    }
    
    /**
     * Clear all textures (local and remote)
     */
    public void clearAllTextures() {
        clearRemoteTextures();
        unloadLocalSkin();
    }
    
    /**
     * Force reload the local skin texture
     */
    public void forceReloadLocalSkin(SkinData skinData) {
        if (localSkinTexture != null) {
            Minecraft.getInstance().getTextureManager().release(localSkinTexture);
        }
        if (localMouthOpenTexture != null) {
            Minecraft.getInstance().getTextureManager().release(localMouthOpenTexture);
        }
        unloadLocalSkin();
        
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        
        loadLocalSkinWithUniqueId(skinData);
    }
    
    /**
     * Load local skin with unique ID to avoid texture caching
     */
    private void loadLocalSkinWithUniqueId(SkinData skinData) {
        if (skinData.getImageData() == null) {
            BBTSkin.LOGGER.warn("Cannot load local skin without image data");
            return;
        }
        
        try {
            long timestamp = System.currentTimeMillis();
            
            // Load main texture
            NativeImage image = NativeImage.read(new ByteArrayInputStream(skinData.getImageData()));
            localDynamicTexture = new DynamicTexture(image);
            
            String uniqueId = "bbtskin/local_" + timestamp;
            localSkinTexture = Minecraft.getInstance().getTextureManager()
                    .register(uniqueId, localDynamicTexture);
            
            // Load mouth-open texture if available
            if (skinData.hasVoiceTexture() && skinData.getMouthOpenData() != null) {
                NativeImage mouthImage = NativeImage.read(new ByteArrayInputStream(skinData.getMouthOpenData()));
                localMouthOpenDynamicTexture = new DynamicTexture(mouthImage);
                
                String mouthUniqueId = "bbtskin/local_mouth_" + timestamp;
                localMouthOpenTexture = Minecraft.getInstance().getTextureManager()
                        .register(mouthUniqueId, localMouthOpenDynamicTexture);
                localHasVoiceTexture = true;
                
                BBTSkin.LOGGER.info("Loaded local skin with voice texture: {}", localSkinTexture);
            } else {
                localHasVoiceTexture = false;
                BBTSkin.LOGGER.info("Loaded local skin texture: {}", localSkinTexture);
            }
            
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to load local skin texture", e);
        }
    }
}
