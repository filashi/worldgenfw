package com.fish.worldgenfw.command;

import com.fish.worldgenfw.config.ClusterConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public class ReloadBlueprintsCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("wgfwreload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            // 清空蓝图缓存和缺失记录，下一次生成或手动命令会重新加载
                            ClusterConfig.getInstance().clearCache();
                            ctx.getSource().sendSuccess(() -> Component.literal("WorldGenFW blueprint cache cleared. Will reload on next usage."), true);
                            LOGGER.info("WorldGenFW blueprint cache cleared via /wgfwreload.");
                            return 1;
                        })
        );
    }
}