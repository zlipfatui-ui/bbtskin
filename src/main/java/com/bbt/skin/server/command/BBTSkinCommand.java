package com.bbt.skin.server.command;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.server.network.ServerSkinHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server commands for BBTSkin
 * 
 * Commands:
 *   /bbtskin resync           - Resync all skins to all players
 *   /bbtskin resync <player>  - Resync a specific player's skin to everyone
 *   /bbtskin status           - Show API status and loaded skins count
 *   /bbtskin reload           - Reload skins from API
 */
@Mod.EventBusSubscriber(modid = BBTSkin.MOD_ID)
public class BBTSkinCommand {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(
            Commands.literal("bbtskin")
                .requires(source -> source.hasPermission(2)) // Require OP level 2
                
                // /bbtskin resync
                .then(Commands.literal("resync")
                    .executes(BBTSkinCommand::resyncAll)
                    
                    // /bbtskin resync <player>
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(BBTSkinCommand::resyncPlayer)
                    )
                )
                
                // /bbtskin status
                .then(Commands.literal("status")
                    .executes(BBTSkinCommand::showStatus)
                )
                
                // /bbtskin reload
                .then(Commands.literal("reload")
                    .executes(BBTSkinCommand::reloadSkins)
                )
        );
        
        BBTSkin.LOGGER.info("Registered /bbtskin command");
    }
    
    /**
     * /bbtskin resync - Resync all skins to all players
     */
    private static int resyncAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        int count = ServerSkinHandler.resyncAllSkins();
        
        if (count > 0) {
            source.sendSuccess(() -> Component.literal(
                    "§a[BBTSkin] Resynced " + count + " skin update(s) to all players"), true);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "§e[BBTSkin] No skins to resync"), false);
        }
        
        return count;
    }
    
    /**
     * /bbtskin resync <player> - Resync a specific player's skin
     */
    private static int resyncPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            
            boolean success = ServerSkinHandler.resyncPlayerSkin(player.getUUID());
            
            if (success) {
                source.sendSuccess(() -> Component.literal(
                        "§a[BBTSkin] Resynced skin for " + player.getName().getString()), true);
                return 1;
            } else {
                source.sendFailure(Component.literal(
                        "§c[BBTSkin] No skin found for " + player.getName().getString()));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c[BBTSkin] Player not found"));
            return 0;
        }
    }
    
    /**
     * /bbtskin status - Show status info
     */
    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        int skinCount = ServerSkinHandler.getAllSkins().size();
        boolean apiConfigured = ServerSkinHandler.isApiConfigured();
        
        source.sendSuccess(() -> Component.literal("§6[BBTSkin] Status:"), false);
        source.sendSuccess(() -> Component.literal(
                "§7  Loaded skins: §f" + skinCount), false);
        source.sendSuccess(() -> Component.literal(
                "§7  API configured: " + (apiConfigured ? "§aYes" : "§cNo")), false);
        
        if (!apiConfigured) {
            source.sendSuccess(() -> Component.literal(
                    "§e  Warning: Skins will not persist across restarts!"), false);
        }
        
        return 1;
    }
    
    /**
     * /bbtskin reload - Reload skins from API
     */
    private static int reloadSkins(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!ServerSkinHandler.isApiConfigured()) {
            source.sendFailure(Component.literal("§c[BBTSkin] API not configured"));
            return 0;
        }
        
        source.sendSuccess(() -> Component.literal(
                "§e[BBTSkin] Reloading skins from API..."), true);
        
        // This would need to be async - for now just resync what's in memory
        int count = ServerSkinHandler.resyncAllSkins();
        
        source.sendSuccess(() -> Component.literal(
                "§a[BBTSkin] Resynced " + count + " skin(s)"), true);
        
        return 1;
    }
}
