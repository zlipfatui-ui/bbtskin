package com.bbt.skin.common.network.packet;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.client.BBTSkinClient;
import com.bbt.skin.common.network.NetworkConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Packet for receiving large skin data in chunks (server -> client)
 */
public class SkinResponseChunkPacket {
    
    public static final int MAX_CHUNK_SIZE = 28000;
    
    // Pending downloads on client
    private static final Map<String, PendingDownload> pendingDownloads = new ConcurrentHashMap<>();
    
    private final String playerUUID;
    private final String skinId;
    private final String skinName;
    private final boolean slim;
    private final int width;
    private final int height;
    private final int totalSize;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] chunkData;
    
    public SkinResponseChunkPacket(String playerUUID, String skinId, String skinName, boolean slim,
                                   int width, int height, int totalSize, int chunkIndex,
                                   int totalChunks, byte[] chunkData) {
        this.playerUUID = playerUUID;
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
    
    public static void encode(SkinResponseChunkPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.playerUUID, NetworkConstants.MAX_STRING_LENGTH);
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
    
    public static SkinResponseChunkPacket decode(FriendlyByteBuf buf) {
        String playerUUID = buf.readUtf(NetworkConstants.MAX_STRING_LENGTH);
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
        
        return new SkinResponseChunkPacket(playerUUID, skinId, skinName, slim, width, height,
                totalSize, chunkIndex, totalChunks, chunkData);
    }
    
    public static void handle(SkinResponseChunkPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet));
        });
        ctx.get().setPacketHandled(true);
    }
    
    private static void handleClient(SkinResponseChunkPacket packet) {
        String key = packet.playerUUID + ":" + packet.skinId;
        
        // Get or create pending download
        PendingDownload pending = pendingDownloads.computeIfAbsent(key,
                k -> new PendingDownload(packet.playerUUID, packet.skinId, packet.skinName,
                        packet.slim, packet.width, packet.height, packet.totalSize, packet.totalChunks));
        
        // If skinId changed, start fresh
        if (!pending.skinId.equals(packet.skinId)) {
            pending = new PendingDownload(packet.playerUUID, packet.skinId, packet.skinName,
                    packet.slim, packet.width, packet.height, packet.totalSize, packet.totalChunks);
            pendingDownloads.put(key, pending);
        }
        
        // Add chunk
        pending.addChunk(packet.chunkIndex, packet.chunkData);
        
        BBTSkin.LOGGER.debug("Received response chunk {}/{} for player {}",
                packet.chunkIndex + 1, packet.totalChunks, packet.playerUUID);
        
        // Check if complete
        if (pending.isComplete()) {
            byte[] fullData = pending.assemble();
            if (fullData != null) {
                BBTSkin.LOGGER.info("Assembled remote skin '{}' ({} bytes)",
                        pending.skinName, fullData.length);
                
                // Process complete skin
                BBTSkinClient client = BBTSkinClient.getInstance();
                if (client != null) {
                    client.handleSkinResponse(pending.playerUUID, pending.skinId, fullData,
                            pending.slim, pending.skinName, pending.width, pending.height);
                }
            }
            pendingDownloads.remove(key);
        }
    }
    
    /**
     * Tracks chunks for a pending download
     */
    private static class PendingDownload {
        final String playerUUID;
        final String skinId;
        final String skinName;
        final boolean slim;
        final int width;
        final int height;
        final int totalSize;
        final int totalChunks;
        final byte[][] chunks;
        int receivedCount = 0;
        
        PendingDownload(String playerUUID, String skinId, String skinName, boolean slim,
                       int width, int height, int totalSize, int totalChunks) {
            this.playerUUID = playerUUID;
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
