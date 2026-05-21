package com.fish.worldgenfw.command;

import com.fish.worldgenfw.config.ClusterConfig;
import com.fish.worldgenfw.structure.StructureTemplateLoader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

public class ReloadBlueprintsCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("wgfwreload")
                        .requires(source -> source.hasPermission(2)) // 需要管理员权限
                        .executes(ctx -> {
                            ResourceManager resourceManager = ctx.getSource().getServer().getResourceManager();
                            ClusterConfig.getInstance().loadBlueprints(resourceManager);
                            StructureTemplateLoader.clearCache();
                            ctx.getSource().sendSuccess(() -> Component.literal("WorldGenFW blueprints reloaded."), true);
                            LOGGER.info("WorldGenFW blueprints manually reloaded via /wgfwreload.");
                            return 1;
                        })
        );
    }
}