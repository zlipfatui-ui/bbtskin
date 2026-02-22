package com.bbt.skin.server.network;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.common.network.NetworkHandler;
import com.bbt.skin.common.network.packet.SkinResponsePacket;
import com.bbt.skin.server.api.SkinApiClient;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import net.minecraftforge.event.TickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side handler for skin synchronization (Forge)
 * Stores skins in memory and persists to Cloudflare Worker API
 */
@Mod.EventBusSubscriber(modid = BBTSkin.MOD_ID)
public class ServerSkinHandler {
    
    // In-memory skin storage
    private static final Map<UUID, PlayerSkinData> playerSkins = new ConcurrentHashMap<>();

    // Pending join sync: UUID -> ticks remaining before sending existing skins
    private static final Map<UUID, Integer> pendingJoinSync = new ConcurrentHashMap<>();
    private static final int JOIN_SYNC_DELAY_TICKS = 40; // ~2 seconds

    // API client (initialized when config is loaded)
    private static SkinApiClient apiClient = null;
    
    /**
     * Initialize API client with config
     */
    public static void initializeApi(String baseUrl, String apiKey) {
        if (baseUrl != null && !baseUrl.isEmpty() && apiKey != null && !apiKey.isEmpty()) {
            apiClient = new SkinApiClient(baseUrl, apiKey);
            BBTSkin.LOGGER.info("BBTSkin API client initialized: {}", baseUrl);
            
            // Load all skins from API on startup
            loadAllSkinsFromApi();
        } else {
            BBTSkin.LOGGER.warn("BBTSkin API not configured - skins will not persist across restarts");
        }
    }
    
    /**
     * Load all skins from API (called on server start)
     */
    private static void loadAllSkinsFromApi() {
        if (apiClient == null) return;
        
        apiClient.getAllSkins().thenAccept(skins -> {
            for (SkinApiClient.SkinApiResponse skin : skins) {
                if (skin.imageData != null) {
                    PlayerSkinData data = new PlayerSkinData(
                            skin.uuid, skin.imageData, skin.slim, 
                            skin.name, skin.width, skin.height
                    );
                    playerSkins.put(UUID.fromString(skin.uuid), data);
                    BBTSkin.LOGGER.info("Loaded skin from API for: {}", skin.name);
                }
            }
            BBTSkin.LOGGER.info("Loaded {} skins from API", skins.size());
        });
    }
    
    /**
     * Data class for storing player skin information
     */
    public static class PlayerSkinData {
        public final String skinId;
        public final byte[] imageData;
        public final boolean isSlim;
        public final String skinName;
        public final int width;
        public final int height;
        
        public PlayerSkinData(String skinId, byte[] imageData, boolean isSlim, 
                             String skinName, int width, int height) {
            this.skinId = skinId;
            this.imageData = imageData;
            this.isSlim = isSlim;
            this.skinName = skinName;
            this.width = width;
            this.height = height;
        }
    }
    
    /**
     * Handle skin sync from a player
     */
    public static void handleSkinSync(ServerPlayer player, String skinId, byte[] skinData, 
                                       boolean isSlim, String skinName, int width, int height) {
        UUID playerUUID = player.getUUID();
        String playerName = player.getName().getString();
        
        // Store in memory
        PlayerSkinData data = new PlayerSkinData(skinId, skinData, isSlim, skinName, width, height);
        playerSkins.put(playerUUID, data);
        
        BBTSkin.LOGGER.info("Player {} uploaded skin: {} ({} bytes)", 
                playerName, skinName, skinData.length);
        
        // Broadcast to all other players
        broadcastSkinUpdate(player);
        
        // Persist to API
        if (apiClient != null) {
            apiClient.saveSkin(playerUUID.toString(), playerName, skinData, isSlim, width, height)
                    .thenAccept(success -> {
                        if (success) {
                            BBTSkin.LOGGER.info("Skin persisted to API for {}", playerName);
                        }
                    });
        }
    }
    
    /**
     * Handle skin request from a player
     */
    public static void handleSkinRequest(ServerPlayer requester, String targetPlayerUUID) {
        UUID targetUUID;
        try {
            targetUUID = UUID.fromString(targetPlayerUUID);
        } catch (IllegalArgumentException e) {
            BBTSkin.LOGGER.warn("Invalid UUID in skin request: {}", targetPlayerUUID);
            return;
        }
        
        // Check memory first
        PlayerSkinData skinData = playerSkins.get(targetUUID);
        if (skinData != null) {
            sendSkinToPlayer(requester, targetPlayerUUID, skinData);
            return;
        }
        
        // Try to fetch from API
        if (apiClient != null) {
            apiClient.getSkin(targetPlayerUUID).thenAccept(response -> {
                if (response != null && response.imageData != null) {
                    PlayerSkinData data = new PlayerSkinData(
                            response.uuid, response.imageData, response.slim,
                            response.name, response.width, response.height
                    );
                    playerSkins.put(targetUUID, data);
                    
                    // Send on main thread
                    var server = ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        server.execute(() -> sendSkinToPlayer(requester, targetPlayerUUID, data));
                    }
                }
            });
        }
    }
    
    /**
     * Handle skin reset from a player
     */
    public static void handleSkinReset(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        playerSkins.remove(playerUUID);
        
        BBTSkin.LOGGER.info("Player {} reset their skin", player.getName().getString());
        
        // Notify other players with empty skin packet
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (!other.equals(player)) {
                    SkinResponsePacket packet = new SkinResponsePacket(playerUUID.toString());
                    NetworkHandler.sendToPlayer(packet, other);
                }
            }
        }
        
        // Remove from API
        if (apiClient != null) {
            apiClient.deleteSkin(playerUUID.toString());
        }
    }
    
    /**
     * Send skin data to a specific player (uses chunked sending for large skins)
     */
    private static void sendSkinToPlayer(ServerPlayer player, String ownerUUID, PlayerSkinData data) {
        try {
            // Use NetworkHandler's chunked sending method
            NetworkHandler.sendSkinToPlayer(
                    ownerUUID,
                    data.skinId,
                    data.imageData,
                    data.isSlim,
                    data.skinName,
                    data.width,
                    data.height,
                    player
            );
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to send skin to player", e);
        }
    }
    
    /**
     * Broadcast skin update to all other players
     */
    private static void broadcastSkinUpdate(ServerPlayer source) {
        UUID sourceUUID = source.getUUID();
        PlayerSkinData data = playerSkins.get(sourceUUID);
        
        if (data == null) return;
        
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (!other.equals(source)) {
                    sendSkinToPlayer(other, sourceUUID.toString(), data);
                }
            }
        }
    }
    
    /**
     * Resync all skins to all players (for /bbtskin resync command)
     */
    public static int resyncAllSkins() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0;
        
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            count += resyncSkinsToPlayer(player);
        }
        
        BBTSkin.LOGGER.info("Resynced {} skin updates to all players", count);
        return count;
    }
    
    /**
     * Resync all skins to a specific player
     */
    public static int resyncSkinsToPlayer(ServerPlayer player) {
        int count = 0;
        for (Map.Entry<UUID, PlayerSkinData> entry : playerSkins.entrySet()) {
            if (!entry.getKey().equals(player.getUUID())) {
                sendSkinToPlayer(player, entry.getKey().toString(), entry.getValue());
                count++;
            }
        }
        return count;
    }
    
    /**
     * Resync a specific player's skin to all others
     */
    public static boolean resyncPlayerSkin(UUID playerUUID) {
        PlayerSkinData data = playerSkins.get(playerUUID);
        if (data == null) return false;
        
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getUUID().equals(playerUUID)) {
                sendSkinToPlayer(player, playerUUID.toString(), data);
            }
        }
        
        BBTSkin.LOGGER.info("Resynced skin for player {}", playerUUID);
        return true;
    }
    
    /**
     * Called when a player joins - queue delayed sync of existing skins
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Queue delayed sync so the client's network channel is ready
            pendingJoinSync.put(player.getUUID(), JOIN_SYNC_DELAY_TICKS);
            BBTSkin.LOGGER.info("Queued skin sync for joining player {} ({}t delay)",
                    player.getName().getString(), JOIN_SYNC_DELAY_TICKS);

            // Check if the joining player has a stored skin in API
            if (apiClient != null) {
                String uuid = player.getUUID().toString();
                apiClient.getSkin(uuid).thenAccept(response -> {
                    if (response != null && response.imageData != null) {
                        PlayerSkinData data = new PlayerSkinData(
                                response.uuid, response.imageData, response.slim,
                                response.name, response.width, response.height
                        );
                        playerSkins.put(player.getUUID(), data);

                        // Broadcast to others on main thread
                        var server = ServerLifecycleHooks.getCurrentServer();
                        if (server != null) {
                            server.execute(() -> {
                                for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                                    if (!other.equals(player)) {
                                        sendSkinToPlayer(other, uuid, data);
                                    }
                                }
                                BBTSkin.LOGGER.info("Loaded and broadcast skin for {} from API",
                                        player.getName().getString());
                            });
                        }
                    }
                });
            }
        }
    }

    /**
     * Server tick handler - processes delayed join syncs
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pendingJoinSync.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, Integer>> it = pendingJoinSync.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    int sent = resyncSkinsToPlayer(player);
                    BBTSkin.LOGGER.info("Sent {} existing skins to player {} (delayed sync)",
                            sent, player.getName().getString());
                }
            } else {
                entry.setValue(remaining);
            }
        }
    }
    
    /**
     * Called when a player leaves
     */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        // Keep skin in memory - it's persisted to API
        // Clean up any pending sync
        pendingJoinSync.remove(event.getEntity().getUUID());
    }
    
    /**
     * Get skin for a player (from memory)
     */
    public static PlayerSkinData getPlayerSkin(UUID playerUUID) {
        return playerSkins.get(playerUUID);
    }
    
    /**
     * Get all stored skins
     */
    public static Collection<Map.Entry<UUID, PlayerSkinData>> getAllSkins() {
        return playerSkins.entrySet();
    }
    
    /**
     * Check if API is configured
     */
    public static boolean isApiConfigured() {
        return apiClient != null;
    }
}
