package com.bbt.skin.common.network.packet;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.common.network.NetworkConstants;
import com.bbt.skin.server.network.ServerSkinHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Packet for sending large skin data in chunks (client -> server)
 * Minecraft's packet limit is 32KB, so we chunk larger skins.
 */
public class SkinChunkPacket {
    
    // Max chunk size - leave room for headers (use 28KB to be safe)
    public static final int MAX_CHUNK_SIZE = 28000;
    
    // Pending uploads on server
    private static final Map<UUID, PendingUpload> pendingUploads = new ConcurrentHashMap<>();
    
    private final String skinId;
    private final String skinName;
    private final boolean slim;
    private final int width;
    private final int height;
    private final int totalSize;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] chunkData;
    
    public SkinChunkPacket(String skinId, String skinName, boolean slim, int width, int height,
                          int totalSize, int chunkIndex, int totalChunks, byte[] chunkData) {
        this.skinId = skinId;
        this.skinName = skinName;
        this.slim = slim;
        this.width = width;
        this.height = height;
        this.totalSize = totalSize;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.chunkData = chunkData;
    }
    
    public static void encode(SkinChunkPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.skinId, NetworkConstants.MAX_STRING_LENGTH);
        buf.writeUtf(packet.skinName, NetworkConstants.MAX_STRING_LENGTH);
        buf.writeBoolean(packet.slim);
        buf.writeInt(packet.width);
        buf.writeInt(packet.height);
        buf.writeInt(packet.totalSize);
        buf.writeInt(packet.chunkIndex);
        buf.writeInt(packet.totalChunks);
        buf.writeInt(packet.chunkData.length);
        buf.writeBytes(packet.chunkData);
    }
    
    public static SkinChunkPacket decode(FriendlyByteBuf buf) {
        String skinId = buf.readUtf(NetworkConstants.MAX_STRING_LENGTH);
        String skinName = buf.readUtf(NetworkConstants.MAX_STRING_LENGTH);
        boolean slim = buf.readBoolean();
        int width = buf.readInt();
        int height = buf.readInt();
        int totalSize = buf.readInt();
        int chunkIndex = buf.readInt();
        int totalChunks = buf.readInt();
        int chunkLen = buf.readInt();
        
        byte[] chunkData = new byte[Math.min(chunkLen, MAX_CHUNK_SIZE + 1000)];
        buf.readBytes(chunkData);
        
        return new SkinChunkPacket(skinId, skinName, slim, width, height,
                totalSize, chunkIndex, totalChunks, chunkData);
    }
    
    public static void handle(SkinChunkPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            
            UUID playerUUID = player.getUUID();
            
            // Get or create pending upload
            PendingUpload pending = pendingUploads.computeIfAbsent(playerUUID,
                    k -> new PendingUpload(packet.skinId, packet.skinName, packet.slim,
                            packet.width, packet.height, packet.totalSize, packet.totalChunks));
            
            // If skinId changed, start fresh
            if (!pending.skinId.equals(packet.skinId)) {
                pending = new PendingUpload(packet.skinId, packet.skinName, packet.slim,
                        packet.width, packet.height, packet.totalSize, packet.totalChunks);
                pendingUploads.put(playerUUID, pending);
            }
            
            // Add chunk
            pending.addChunk(packet.chunkIndex, packet.chunkData);
            
            BBTSkin.LOGGER.debug("Received chunk {}/{} for skin '{}' from {}",
                    packet.chunkIndex + 1, packet.totalChunks, packet.skinName, 
                    player.getName().getString());
            
            // Check if complete
            if (pending.isComplete()) {
                byte[] fullData = pending.assemble();
                if (fullData != null) {
                    BBTSkin.LOGGER.info("Assembled skin '{}' ({} bytes) from {} chunks",
                            pending.skinName, fullData.length, pending.totalChunks);
                    
                    // Process complete skin
                    ServerSkinHandler.handleSkinSync(player, pending.skinId, fullData,
                            pending.slim, pending.skinName, pending.width, pending.height);
                }
                pendingUploads.remove(playerUUID);
            }
        });
        ctx.get().setPacketHandled(true);
    }
    
    /**
     * Tracks chunks for a pending upload
     */
    private static class PendingUpload {
        final String skinId;
        final String skinName;
        final boolean slim;
        final int width;
        final int height;
        final int totalSize;
        final int totalChunks;
        final byte[][] chunks;
        int receivedCount = 0;
        
        PendingUpload(String skinId, String skinName, boolean slim, int width, int height,
                     int totalSize, int totalChunks) {
            this.skinId = skinId;
            this.skinName = skinName;
            this.slim = slim;
            this.width = width;
            this.height = height;
            this.totalSize = totalSize;
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
        }
        
        void addChunk(int index, byte[] data) {
            if (index >= 0 && index < totalChunks && chunks[index] == null) {
                chunks[index] = data;
                receivedCount++;
            }
        }
        
        boolean isComplete() {
            return receivedCount >= totalChunks;
        }
        
        byte[] assemble() {
            int actualSize = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) actualSize += chunk.length;
            }
            
            byte[] result = new byte[actualSize];
            int offset = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    System.arraycopy(chunk, 0, result, offset, chunk.length);
                    offset += chunk.length;
                }
            }
            return result;
        }
    }
}
