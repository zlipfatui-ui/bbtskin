package com.bbt.skin.common.network.packet;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.client.BBTSkinClient;
import com.bbt.skin.common.network.NetworkConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from server to client to notify about skin sync events
 */
public class SkinApplyPacket {
    
    private final String playerUUID;
    private final boolean hasNewSkin;
    
    public SkinApplyPacket(String playerUUID, boolean hasNewSkin) {
        this.playerUUID = playerUUID;
        this.hasNewSkin = hasNewSkin;
    }
    
    public static void encode(SkinApplyPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.playerUUID, NetworkConstants.MAX_STRING_LENGTH);
        buf.writeBoolean(packet.hasNewSkin);
    }
    
    public static SkinApplyPacket decode(FriendlyByteBuf buf) {
        return new SkinApplyPacket(
                buf.readUtf(NetworkConstants.MAX_STRING_LENGTH),
                buf.readBoolean()
        );
    }
    
    public static void handle(SkinApplyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet));
        });
        ctx.get().setPacketHandled(true);
    }
    
    private static void handleClient(SkinApplyPacket packet) {
        // Request the full skin data if we don't have it
        if (packet.hasNewSkin) {
            BBTSkinClient.requestPlayerSkin(packet.playerUUID);
        }
    }
    
    public String getPlayerUUID() { return playerUUID; }
    public boolean hasNewSkin() { return hasNewSkin; }
}
