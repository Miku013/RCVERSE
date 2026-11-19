package com.microdrone.mod.cinematic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 撮影用モード切り替えコマンド: /rc cinematic [on|off|<radius>]
 */
public class CinematicCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rc")
            .then(Commands.literal("cinematic")
                .executes(ctx -> {
                    boolean enabled = CinematicRecordingManager.isEnabled();
                    double radius = CinematicRecordingManager.getBroadcastRadius();
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                        "chat.microdrone.cinematic.status",
                        enabled ? "§aON" : "§cOFF",
                        radius
                    ), false);
                    return 1;
                })
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        CinematicRecordingManager.setEnabled(true);
                        double radius = CinematicRecordingManager.getBroadcastRadius();
                        ctx.getSource().sendSuccess(() -> Component.translatable(
                            "chat.microdrone.cinematic.broadcast_on",
                            radius
                        ), true);
                        return 1;
                    })
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(5.0, 256.0))
                        .executes(ctx -> {
                            double r = DoubleArgumentType.getDouble(ctx, "radius");
                            CinematicRecordingManager.setEnabled(true);
                            CinematicRecordingManager.setBroadcastRadius(r);
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                "chat.microdrone.cinematic.broadcast_radius",
                                r
                            ), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        CinematicRecordingManager.setEnabled(false);
                        ctx.getSource().sendSuccess(() -> Component.translatable(
                            "chat.microdrone.cinematic.broadcast_off"
                        ), true);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("dummy")
                .executes(ctx -> {
                    boolean cur = CinematicRecordingManager.isSpawnDummy();
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                        "chat.microdrone.cinematic.dummy_status",
                        cur ? "§aON" : "§cOFF"
                    ), false);
                    return 1;
                })
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        CinematicRecordingManager.setSpawnDummy(true);
                        ctx.getSource().sendSuccess(() -> Component.translatable(
                            "chat.microdrone.cinematic.dummy_on"
                        ), true);
                        return 1;
                    })
                )
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        CinematicRecordingManager.setSpawnDummy(false);
                        ctx.getSource().sendSuccess(() -> Component.translatable(
                            "chat.microdrone.cinematic.dummy_off"
                        ), true);
                        return 1;
                    })
                )
            )
        );
    }
}
