package com.microdrone.mod.network;

import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.entity.RcBoatEntity;
import com.microdrone.mod.entity.RcPlaneEntity;
import com.microdrone.mod.entity.RcSubmarineEntity;
import com.microdrone.mod.entity.RcTankEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerPayloadHandler {

    public static void handleDroneControl(DroneControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(payload.droneId());
                if (entity instanceof DroneEntity drone) {
                    if (drone.isPilotedBy(player)) {
                        if (payload.stop()) {
                            drone.stopPiloting(player);
                        } else {
                            drone.applyControlInput(
                                    payload.forward(),
                                    payload.strafe(),
                                    payload.vertical(),
                                    payload.yaw(),
                                    payload.pitch()
                            );
                        }
                    }
                } else if (entity instanceof RcCarEntity car) {
                    if (car.isPilotedBy(player)) {
                        if (payload.stop()) {
                            car.stopPiloting(player);
                        } else {
                            car.inputForward = payload.forward();
                            car.inputSteer = payload.strafe();
                            car.inputHandbrake = (payload.vertical() > 0.5f); // スペースキーでハンドブレーキ
                            // クライアントの正確な機首向き (Yaw) を完全同期！(進行方向のズレを根絶)
                            car.setYRot(payload.yaw());
                            car.yRotO = payload.yaw();

                            // クライアント主導の位置・速度同期 (ステップ2: サーバー追従)
                            if (payload.hasClientPos()) {
                                car.applyClientPositionSync(
                                        payload.clientX(),
                                        payload.clientY(),
                                        payload.clientZ(),
                                        payload.clientSpeed(),
                                        payload.yaw()
                                );
                            }
                        }
                    }
                } else if (entity instanceof RcTankEntity tank) {
                    if (tank.isPilotedBy(player)) {
                        if (payload.stop()) {
                            tank.stopPiloting(player);
                        } else {
                            tank.inputForward = payload.forward();
                            tank.inputSteer = payload.strafe();
                            tank.inputFire = (payload.vertical() > 0.5f); // スペースキーで主砲発射！

                            // プレイヤーの視線から砲塔の相対旋回角・砲身仰俯角を算出
                            float relativeYaw = Mth.wrapDegrees(payload.yaw() - tank.getYRot());
                            tank.targetTurretYaw = relativeYaw;
                            tank.targetCannonPitch = payload.pitch();
                        }
                    }
                } else if (entity instanceof RcPlaneEntity plane) {
                    if (plane.isPilotedBy(player)) {
                        if (payload.stop()) {
                            plane.stopPiloting(player);
                        } else {
                            // 航空機操縦入力:
                            // W: ピッチダウン(正), S: ピッチアップ/上昇(負)
                            plane.inputElevator = -payload.forward();
                            // A: 左ロール(負), D: 右ロール(正) (クライアントと符号整合)
                            plane.inputAileron = -payload.strafe();
                            // Space: スロットル増(+1.0), Shift: スロットル減(-1.0)
                            plane.inputThrottleDelta = payload.vertical();
                            // ラダー / ヨー旋回
                            plane.inputRudder = payload.yaw();
                        }
                    }
                } else if (entity instanceof RcBoatEntity boat) {
                    if (boat.isPilotedBy(player)) {
                        if (payload.stop()) {
                            boat.stopPiloting(player);
                        } else {
                            boat.inputForward = payload.forward();
                            boat.inputSteer = payload.strafe();
                        }
                    }
                } else if (entity instanceof RcSubmarineEntity submarine) {
                    if (submarine.isPilotedBy(player)) {
                        if (payload.stop()) {
                            submarine.stopPiloting(player);
                        } else {
                            submarine.inputForward = payload.forward();
                            submarine.inputSteer = payload.strafe();
                            submarine.inputVertical = payload.vertical();
                        }
                    }
                }

                // 撮影用ブロードキャスト同時操作（Cinematic Multi-Vehicle Control）
                if (com.microdrone.mod.cinematic.CinematicRecordingManager.isEnabled()) {
                    com.microdrone.mod.cinematic.CinematicRecordingManager.broadcastControlInput(player, entity, payload);
                }
            }
        });
    }

    public static void handlePlaneModeToggle(PlaneModeTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(payload.planeId());
                if (entity instanceof RcPlaneEntity plane && plane.isPilotedBy(player)) {
                    plane.setFlightMode(payload.mode());
                }
            }
        });
    }

    public static void handleRcCarTuning(RcCarTuningPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(payload.carEntityId());
                if (entity instanceof RcCarEntity car) {
                    car.setCustomWeight(payload.weightKg());
                    car.setDrivingMode(payload.realMode());
                    car.setCounterMaxAngle(payload.counterMaxAngleDeg());
                    car.setDriftStartAngle(payload.driftStartAngleDeg());
                }
            }
        });
    }

    public static void handleRoadConfig(RoadConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (player.level().getBlockEntity(payload.startPos()) instanceof com.microdrone.mod.block.road.RoadStartBlockEntity startBe) {
                    var data = startBe.getSplineData();
                    data.setRoadMode(com.microdrone.mod.block.road.RoadSplineData.RoadMode.fromId(payload.roadMode()));
                    data.setRoadWidth(payload.roadWidth());
                    data.setMaxBankAngle(payload.maxBankAngle());
                    data.setTurnDirection(payload.turnDirection());
                    data.setControlPointDistance(payload.controlPointDistance());
                    data.setSegments(payload.segments());
                    data.setArcFitMode(payload.arcFitMode());
                    data.setLoopHeight(payload.loopHeight());
                    data.setLoopBoostMultiplier(payload.loopBoostMultiplier());
                    data.setPreview(payload.isPreview());
                    data.setGenerated(payload.isGenerated());

                    if (payload.action() == 1) { // 終点再探索
                        startBe.autoFindNearestEndBlock(32);
                    } else if (payload.action() == 2) { // 確定生成
                        data.setGenerated(true);
                        data.setPreview(false);
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("chat.microdrone.road_generated"), true);
                    } else if (payload.action() == 3) { // 道路削除
                        data.setGenerated(false);
                        data.setPreview(true);
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("chat.microdrone.road_deleted"), true);
                    }

                    startBe.setChanged();
                    com.microdrone.mod.block.road.RoadSplineManager.registerRoad(player.level(), startBe);
                    player.level().sendBlockUpdated(payload.startPos(), startBe.getBlockState(), startBe.getBlockState(), 3);
                }
            }
        });
    }

    public static void handleCarHeadlightToggle(CarHeadlightTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(payload.carId());
                if (entity instanceof RcCarEntity car && car.isPilotedBy(player)) {
                    car.toggleHeadlight();
                }
            }
        });
    }

    public static void handleUpdateDecals(UpdateDecalsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                var be = player.level().getBlockEntity(payload.tablePos());
                if (be instanceof com.microdrone.mod.block.entity.DecalTableBlockEntity table) {
                    if (!table.getStoredCar().isEmpty()) {
                        ItemStack carStack = table.getStoredCar();
                        net.minecraft.world.item.component.CustomData.update(
                                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                carStack,
                                tag -> tag.put("Decals", payload.decalsTag()));
                        table.setStoredCar(carStack);
                        table.setChanged();
                        player.level().sendBlockUpdated(payload.tablePos(), table.getBlockState(), table.getBlockState(), 3);

                        if (payload.takeOut()) {
                            // 適用して手元に回収
                            table.setStoredCar(ItemStack.EMPTY);
                            table.setChanged();
                            player.level().sendBlockUpdated(payload.tablePos(), table.getBlockState(), table.getBlockState(), 3);
                            player.level().playSound(null, payload.tablePos(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
                            if (!player.getInventory().add(carStack)) {
                                player.drop(carStack, false);
                            }
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("chat.microdrone.decals_saved_and_taken"), true);
                        } else {
                            // 適用して作業台に残す
                            player.level().playSound(null, payload.tablePos(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.2F);
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("chat.microdrone.decals_saved"), true);
                        }
                    }
                }
            }
        });
    }

    public static void handleCancelDecal(CancelDecalPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                var be = player.level().getBlockEntity(payload.tablePos());
                if (be instanceof com.microdrone.mod.block.entity.DecalTableBlockEntity table) {
                    if (payload.returnToPlayer() && !table.getStoredCar().isEmpty()) {
                        ItemStack car = table.getStoredCar();
                        table.setStoredCar(ItemStack.EMPTY);
                        table.setChanged();
                        player.level().sendBlockUpdated(payload.tablePos(), table.getBlockState(), table.getBlockState(), 3);
                        player.level().playSound(null, payload.tablePos(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
                        if (!player.getInventory().add(car)) {
                            player.drop(car, false);
                        }
                    }
                }
            }
        });
    }
}
