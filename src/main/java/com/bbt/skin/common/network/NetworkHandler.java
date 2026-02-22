package com.bbt.skin.common.network;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.common.network.packet.SkinApplyPacket;
import com.bbt.skin.common.network.packet.SkinChunkPacket;
import com.bbt.skin.common.network.packet.SkinRequestPacket;
import com.bbt.skin.common.network.packet.SkinResetPacket;
import com.bbt.skin.common.network.packet.SkinResponseChunkPacket;
import com.bbt.skin.common.network.packet.SkinResponsePacket;
import com.bbt.skin.common.network.packet.SkinSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Handles network packet registration and sending for BBTSkin (Forge 1.20.1)
 */
public class NetworkHandler {
    
    private static final String PROTOCOL_VERSION = "1";
    
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BBTSkin.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    
    private static int packetId = 0;
    
    public static void register() {
        // Client -> Server packets
        CHANNEL.registerMessage(packetId++, SkinSyncPacket.class,
                SkinSyncPacket::encode,
                SkinSyncPacket::decode,
                SkinSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        
        CHANNEL.registerMessage(packetId++, SkinChunkPacket.class,
                SkinChunkPacket::encode,
                SkinChunkPacket::decode,
                SkinChunkPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        
        CHANNEL.registerMessage(packetId++, SkinRequestPacket.class,
                SkinRequestPacket::encode,
                SkinRequestPacket::decode,
                SkinRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        
        CHANNEL.registerMessage(packetId++, SkinResetPacket.class,
                SkinResetPacket::encode,
                SkinResetPacket::decode,
                SkinResetPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        
        // Server -> Client packets
        CHANNEL.registerMessage(packetId++, SkinResponsePacket.class,
                SkinResponsePacket::encode,
                SkinResponsePacket::decode,
                SkinResponsePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        
        CHANNEL.registerMessage(packetId++, SkinResponseChunkPacket.class,
                SkinResponseChunkPacket::encode,
                SkinResponseChunkPacket::decode,
                SkinResponseChunkPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        
        CHANNEL.registerMessage(packetId++, SkinApplyPacket.class,
                SkinApplyPacket::encode,
                SkinApplyPacket::decode,
                SkinApplyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        
        BBTSkin.LOGGER.info("Registered {} network packets", packetId);
    }
    
    // Utility methods for sending packets
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
    
    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    
    public static void sendToAllPlayers(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
    
    /**
     * Send skin data to server, using chunked packets for large skins
     */
    public static void sendSkinToServer(String skinId, byte[] skinData, boolean slim, 
                                        String skinName, int width, int height) {
        // Check if we need chunking (> 30KB)
        if (skinData.length <= SkinChunkPacket.MAX_CHUNK_SIZE) {
            // Small enough for single packet
            sendToServer(new SkinSyncPacket(skinId, skinData, slim, skinName, width, height));
        } else {
            // Need to chunk
            sendChunkedSkinToServer(skinId, skinData, slim, skinName, width, height);
        }
    }
    
    /**
     * Send large skin data in chunks
     */
    private static void sendChunkedSkinToServer(String skinId, byte[] skinData, boolean slim,
                                                 String skinName, int width, int height) {
        int totalSize = skinData.length;
        int totalChunks = (int) Math.ceil((double) totalSize / SkinChunkPacket.MAX_CHUNK_SIZE);
        
        BBTSkin.LOGGER.info("Sending skin {} in {} chunks ({} bytes)", skinName, totalChunks, totalSize);
        
        for (int i = 0; i < totalChunks; i++) {
            int offset = i * SkinChunkPacket.MAX_CHUNK_SIZE;
            int length = Math.min(SkinChunkPacket.MAX_CHUNK_SIZE, totalSize - offset);
            
            byte[] chunkData = new byte[length];
            System.arraycopy(skinData, offset, chunkData, 0, length);
            
            SkinChunkPacket packet = new SkinChunkPacket(
                    skinId, skinName, slim, width, height,
                    totalSize, i, totalChunks, chunkData
            );
            
            sendToServer(packet);
        }
    }
    
    /**
     * Send skin data to a player, using chunked packets for large skins
     */
    public static void sendSkinToPlayer(String playerUUID, String skinId, byte[] skinData, 
                                        boolean slim, String skinName, int width, int height,
                                        ServerPlayer targetPlayer) {
        // Check if we need chunking
        if (skinData.length <= SkinResponseChunkPacket.MAX_CHUNK_SIZE) {
            // Small enough for single packet
            sendToPlayer(new SkinResponsePacket(playerUUID, skinId, skinData, slim, skinName, width, height), 
                    targetPlayer);
        } else {
            // Need to chunk
            sendChunkedSkinToPlayer(playerUUID, skinId, skinData, slim, skinName, width, height, targetPlayer);
        }
    }
    
    /**
     * Send large skin data to player in chunks
     */
    private static void sendChunkedSkinToPlayer(String playerUUID, String skinId, byte[] skinData,
                                                 boolean slim, String skinName, int width, int height,
                                                 ServerPlayer targetPlayer) {
        int totalSize = skinData.length;
        int totalChunks = (int) Math.ceil((double) totalSize / SkinResponseChunkPacket.MAX_CHUNK_SIZE);
        
        BBTSkin.LOGGER.debug("Sending skin to {} in {} chunks", targetPlayer.getName().getString(), totalChunks);
        
        for (int i = 0; i < totalChunks; i++) {
            int offset = i * SkinResponseChunkPacket.MAX_CHUNK_SIZE;
            int length = Math.min(SkinResponseChunkPacket.MAX_CHUNK_SIZE, totalSize - offset);
            
            byte[] chunkData = new byte[length];
            System.arraycopy(skinData, offset, chunkData, 0, length);
            
            SkinResponseChunkPacket packet = new SkinResponseChunkPacket(
                    playerUUID, skinId, skinName, slim, width, height,
                    totalSize, i, totalChunks, chunkData
            );
            
            sendToPlayer(packet, targetPlayer);
        }
    }
}
