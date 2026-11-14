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
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§b[RCVERSE 撮影モード]§r 現在: " + (enabled ? "§a有効 (ON)" : "§c無効 (OFF)")
                        + " §7(同調半径: " + String.format("%.0f", radius) + "m)§r\n"
                        + "§7使い方: /rc cinematic <on|off> [radius]"
                    ), false);
                    return 1;
                })
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        CinematicRecordingManager.setEnabled(true);
                        double radius = CinematicRecordingManager.getBroadcastRadius();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§b[RCVERSE 撮影モード]§r §a複数台同時操作 (ブロードキャスト) を有効化しました！§7 (同調半径: " + String.format("%.0f", radius) + "m)"
                        ), true);
                        return 1;
                    })
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(5.0, 256.0))
                        .executes(ctx -> {
                            double r = DoubleArgumentType.getDouble(ctx, "radius");
                            CinematicRecordingManager.setEnabled(true);
                            CinematicRecordingManager.setBroadcastRadius(r);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§b[RCVERSE 撮影モード]§r §a有効化 (同調半径を " + String.format("%.0f", r) + "m に設定しました)"
                            ), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        CinematicRecordingManager.setEnabled(false);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§b[RCVERSE 撮影モード]§r §c通常操作（単独操縦）に戻しました。"
                        ), true);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("dummy")
                .executes(ctx -> {
                    boolean cur = CinematicRecordingManager.isSpawnDummy();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§b[RCVERSE 撮影モード]§r ダミー人形生成: " + (cur ? "§a有効 (ON)" : "§c無効 (OFF - 視点ロック解除中)")
                    ), false);
                    return 1;
                })
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        CinematicRecordingManager.setSpawnDummy(true);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§b[RCVERSE 撮影モード]§r §aダミー人形生成を有効化しました。"
                        ), true);
                        return 1;
                    })
                )
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        CinematicRecordingManager.setSpawnDummy(false);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§b[RCVERSE 撮影モード]§r §cダミー人形生成を無効化（視点ロック完全解除）しました。"
                        ), true);
                        return 1;
                    })
                )
            )
        );
    }
}
