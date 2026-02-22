package com.bbt.skin.client.voice;

import com.bbt.skin.BBTSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compatibility layer for Plasmo Voice integration.
 * Detects when players are speaking via voice chat using reflection.
 * 
 * Based on how FragmentSkin and TalkingHeads mods detect talking state.
 */
public class PlasmoVoiceCompat {
    
    private static boolean initialized = false;
    private static boolean plasmoVoicePresent = false;
    private static boolean apiAvailable = false;
    
    // Cached reflection objects
    private static Object voiceClient = null;
    private static Method getSourceManagerMethod = null;
    private static Method getAudioCaptureMethod = null;
    
    // Track talking state per player UUID
    private static final Map<UUID, TalkingInfo> talkingPlayers = new ConcurrentHashMap<>();
    
    // Timing constants
    private static final long TALKING_TIMEOUT_MS = 200;
    private static final long CLEANUP_INTERVAL_MS = 5000;
    private static long lastCleanupTime = 0;
    
    public static void init() {
        if (initialized) return;
        initialized = true;
        
        plasmoVoicePresent = ModList.get().isLoaded("plasmovoice");
        
        if (plasmoVoicePresent) {
            BBTSkin.LOGGER.info("BBTSkin: Plasmo Voice detected! Enabling mouth animation.");
            initializeApi();
        } else {
            BBTSkin.LOGGER.info("BBTSkin: Plasmo Voice not found. Mouth animation disabled.");
        }
    }
    
    private static void initializeApi() {
        try {
            Class<?> apiClass = Class.forName("su.plo.voice.api.client.PlasmoVoiceClient");
            Method getApiMethod = apiClass.getMethod("getApi");
            Optional<?> apiOpt = (Optional<?>) getApiMethod.invoke(null);
            
            if (apiOpt.isPresent()) {
                voiceClient = apiOpt.get();
                
                try {
                    getAudioCaptureMethod = voiceClient.getClass().getMethod("getAudioCapture");
                } catch (NoSuchMethodException ignored) {}
                
                try {
                    getSourceManagerMethod = voiceClient.getClass().getMethod("getSourceManager");
                } catch (NoSuchMethodException ignored) {}
                
                apiAvailable = true;
                BBTSkin.LOGGER.info("BBTSkin: Plasmo Voice API initialized");
            }
        } catch (Exception e) {
            BBTSkin.LOGGER.debug("BBTSkin: API init failed, trying internal: {}", e.getMessage());
            tryInternalApi();
        }
    }
    
    private static void tryInternalApi() {
        try {
            Class<?> modVoiceClientClass = Class.forName("su.plo.voice.client.ModVoiceClient");
            Field instanceField = modVoiceClientClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            voiceClient = instanceField.get(null);
            
            if (voiceClient != null) {
                apiAvailable = true;
                BBTSkin.LOGGER.info("BBTSkin: Plasmo Voice internal API accessed");
            }
        } catch (Exception e) {
            BBTSkin.LOGGER.debug("BBTSkin: Internal API failed: {}", e.getMessage());
        }
    }
    
    public static boolean isAvailable() {
        // Return true if Plasmo Voice mod is loaded - API might not be ready immediately
        // but the mod is present and features should be enabled
        return plasmoVoicePresent;
    }
    
    public static boolean isApiReady() {
        return plasmoVoicePresent && apiAvailable;
    }
    
    public static boolean isLocalPlayerTalking() {
        if (!apiAvailable || voiceClient == null) return false;
        
        try {
            if (getAudioCaptureMethod != null) {
                Object audioCapture = getAudioCaptureMethod.invoke(voiceClient);
                if (audioCapture != null) {
                    Method isActiveMethod = findMethod(audioCapture.getClass(), 
                        "isActivated", "isActive", "isCapturing");
                    if (isActiveMethod != null) {
                        Boolean active = (Boolean) isActiveMethod.invoke(audioCapture);
                        if (active != null && active) return true;
                    }
                }
            }
            
            Method getActivationManager = findMethod(voiceClient.getClass(), "getActivationManager");
            if (getActivationManager != null) {
                Object manager = getActivationManager.invoke(voiceClient);
                if (manager != null) {
                    Method getActivations = findMethod(manager.getClass(), "getActivations");
                    if (getActivations != null) {
                        Object activations = getActivations.invoke(manager);
                        if (activations instanceof Iterable<?>) {
                            for (Object activation : (Iterable<?>) activations) {
                                Method isAct = findMethod(activation.getClass(), "isActivated", "isActive");
                                if (isAct != null) {
                                    Boolean active = (Boolean) isAct.invoke(activation);
                                    if (active != null && active) return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        
        return false;
    }
    
    public static boolean isPlayerTalking(Player player) {
        if (player == null || !apiAvailable) return false;
        
        UUID uuid = player.getUUID();
        Minecraft mc = Minecraft.getInstance();
        
        if (mc.player != null && mc.player.getUUID().equals(uuid)) {
            boolean talking = isLocalPlayerTalking();
            if (talking) setPlayerTalking(uuid, true);
            return talking || isRecentlyTalking(uuid);
        }
        
        return isRecentlyTalking(uuid);
    }
    
    private static boolean isRecentlyTalking(UUID uuid) {
        TalkingInfo info = talkingPlayers.get(uuid);
        if (info == null) return false;
        return System.currentTimeMillis() - info.lastTalkTime < TALKING_TIMEOUT_MS;
    }
    
    public static void setPlayerTalking(UUID playerUUID, boolean talking) {
        if (talking) {
            talkingPlayers.compute(playerUUID, (uuid, info) -> {
                if (info == null) info = new TalkingInfo();
                info.lastTalkTime = System.currentTimeMillis();
                info.isTalking = true;
                return info;
            });
        } else {
            TalkingInfo info = talkingPlayers.get(playerUUID);
            if (info != null) info.isTalking = false;
        }
    }
    
    public static void tick() {
        if (!apiAvailable) return;
        
        long now = System.currentTimeMillis();
        
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            talkingPlayers.entrySet().removeIf(e -> 
                now - e.getValue().lastTalkTime > CLEANUP_INTERVAL_MS);
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isLocalPlayerTalking()) {
            setPlayerTalking(mc.player.getUUID(), true);
        }
        
        updateFromSources();
    }
    
    private static void updateFromSources() {
        if (voiceClient == null || getSourceManagerMethod == null) return;
        
        try {
            Object sourceManager = getSourceManagerMethod.invoke(voiceClient);
            if (sourceManager == null) return;
            
            Method getSources = findMethod(sourceManager.getClass(), "getSources", "getPlayerSources");
            if (getSources == null) return;
            
            Object sources = getSources.invoke(sourceManager);
            if (!(sources instanceof Iterable<?>)) return;
            
            for (Object source : (Iterable<?>) sources) {
                try {
                    Method getPlayerInfo = findMethod(source.getClass(), "getPlayerInfo", "getPlayer");
                    if (getPlayerInfo == null) continue;
                    
                    Object playerInfo = getPlayerInfo.invoke(source);
                    if (playerInfo == null) continue;
                    
                    Method getId = findMethod(playerInfo.getClass(), "getId", "getUuid", "getUUID");
                    if (getId == null) continue;
                    
                    Object idObj = getId.invoke(playerInfo);
                    UUID playerId = idObj instanceof UUID ? (UUID) idObj : 
                        (idObj != null ? UUID.fromString(idObj.toString()) : null);
                    if (playerId == null) continue;
                    
                    Method isActive = findMethod(source.getClass(), "isActivated", "isActive", "isPlaying");
                    if (isActive == null) continue;
                    
                    Boolean active = (Boolean) isActive.invoke(source);
                    if (active != null && active) {
                        setPlayerTalking(playerId, true);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }
    
    private static Method findMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                return clazz.getMethod(name);
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }
    
    private static class TalkingInfo {
        long lastTalkTime = 0;
        boolean isTalking = false;
    }
}
