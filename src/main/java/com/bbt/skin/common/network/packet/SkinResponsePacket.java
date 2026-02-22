package com.bbt.skin.common.network.packet;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.client.BBTSkinClient;
import com.bbt.skin.common.data.SkinData;
import com.bbt.skin.common.network.NetworkConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet sent from server to client with another player's skin data
 */
public class SkinResponsePacket {
    
    private final String playerUUID;
    private final String skinId;
    private final byte[] skinData;
    private final boolean slim;
    private final String skinName;
    private final int width;
    private final int height;
    
    public SkinResponsePacket(String playerUUID, String skinId, byte[] skinData, 
                              boolean slim, String skinName, int width, int height) {
        this.playerUUID = playerUUID;
        this.skinId = skinId;
        this.skinData = skinData;
        this.slim = slim;
        this.skinName = skinName;
        this.width = width;
        this.height = height;
    }
    
    // Empty packet for reset notification
    public SkinResponsePacket(String playerUUID) {
        this(playerUUID, "", new byte[0], false, "", 64, 64);
    }
    
    public static void encode(SkinResponsePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.playerUUID, NetworkConstants.MAX_STRING_LENGTH);
        buf.writeUtf(packet.skinId, NetworkConstants.MAX_STRING_LENGTH);
        buf.writeInt(packet.skinData.length);
        if (packet.skinData.length > 0) {
            buf.writeBytes(packet.skinData);
        }
        buf.writeBoolean(packet.slim);
        buf.writeUtf(packet.skinName, NetworkConstants.MAX_STRING_LENGTH);
        buf.writeInt(packet.width);
        buf.writeInt(packet.height);
    }
    
    public static SkinResponsePacket decode(FriendlyByteBuf buf) {
        String playerUUID = buf.readUtf(NetworkConstants.MAX_STRING_LENGTH);
        String skinId = buf.readUtf(NetworkConstants.MAX_STRING_LENGTH);
        int dataLength = buf.readInt();
        
        byte[] skinData = new byte[0];
        if (dataLength > 0 && dataLength <= NetworkConstants.MAX_SKIN_SIZE) {
            skinData = new byte[dataLength];
            buf.readBytes(skinData);
        }
        
        boolean slim = buf.readBoolean();
        String skinName = buf.readUtf(NetworkConstants.MAX_STRING_LENGTH);
        int width = buf.readInt();
        int height = buf.readInt();
        
        return new SkinResponsePacket(playerUUID, skinId, skinData, slim, skinName, width, height);
    }
    
    public static void handle(SkinResponsePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Handle on client side only
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet));
        });
        ctx.get().setPacketHandled(true);
    }
    
    private static void handleClient(SkinResponsePacket packet) {
        if (packet.skinData.length > 0) {
            // Build skin data
            SkinData remoteSkin = new SkinData.Builder()
                    .id(packet.skinId)
                    .name(packet.skinName)
                    .width(packet.width)
                    .height(packet.height)
                    .slim(packet.slim)
                    .ownerUUID(packet.playerUUID)
                    .imageData(packet.skinData)
                    .build();
            
            try {
                UUID uuid = UUID.fromString(packet.playerUUID);
                BBTSkinClient.getInstance().getTextureManager().loadRemoteSkin(uuid, remoteSkin);
                BBTSkin.LOGGER.info("Received skin for player {}", packet.playerUUID);
            } catch (IllegalArgumentException e) {
                BBTSkin.LOGGER.warn("Invalid UUID in skin response: {}", packet.playerUUID);
            }
        } else {
            // Reset notification - player cleared their skin
            try {
                UUID uuid = UUID.fromString(packet.playerUUID);
                BBTSkinClient.getInstance().getTextureManager().unloadRemoteSkin(uuid);
                BBTSkin.LOGGER.info("Cleared skin for player {}", packet.playerUUID);
            } catch (IllegalArgumentException e) {
                BBTSkin.LOGGER.warn("Invalid UUID in skin reset: {}", packet.playerUUID);
            }
        }
    }
    
    // Getters
    public String getPlayerUUID() { return playerUUID; }
    public String getSkinId() { return skinId; }
    public byte[] getSkinData() { return skinData; }
    public boolean isSlim() { return slim; }
    public String getSkinName() { return skinName; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
