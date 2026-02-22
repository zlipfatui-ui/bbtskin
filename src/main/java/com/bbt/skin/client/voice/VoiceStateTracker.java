package com.bbt.skin.client.voice;

import com.bbt.skin.BBTSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Tracks voice chat state for all players.
 * Used to determine which mouth texture to display.
 */
public class VoiceStateTracker {
    
    private static VoiceStateTracker instance;
    private boolean initialized = false;
    
    private VoiceStateTracker() {}
    
    public static VoiceStateTracker getInstance() {
        if (instance == null) {
            instance = new VoiceStateTracker();
        }
        return instance;
    }
    
    /**
     * Initialize voice state tracking
     */
    public void init() {
        if (initialized) return;
        initialized = true;
        
        PlasmoVoiceCompat.init();
        BBTSkin.LOGGER.info("Voice state tracker initialized");
    }
    
    /**
     * Update called each client tick
     */
    public void tick() {
        PlasmoVoiceCompat.tick();
    }
    
    /**
     * Check if a player should show the "mouth open" texture
     */
    public boolean shouldShowMouthOpen(Player player) {
        if (player == null) return false;
        return PlasmoVoiceCompat.isPlayerTalking(player);
    }
    
    /**
     * Check if local player should show mouth open
     */
    public boolean shouldLocalPlayerShowMouthOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return shouldShowMouthOpen(mc.player);
    }
    
    /**
     * Check if voice chat integration is available
     */
    public boolean isVoiceChatAvailable() {
        return PlasmoVoiceCompat.isAvailable();
    }
    
    /**
     * Manually set talking state (for testing or external triggers)
     */
    public void setTalking(UUID playerUUID, boolean talking) {
        PlasmoVoiceCompat.setPlayerTalking(playerUUID, talking);
    }
}
