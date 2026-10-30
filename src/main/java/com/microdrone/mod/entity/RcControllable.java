package com.microdrone.mod.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * プロポ（RCコントローラー）で操縦可能なRC機体（ドローン、RCカー、戦車、ボート等）の共通インターフェース
 */
public interface RcControllable {
    /** 操縦中かどうか */
    boolean isPiloted();

    /** 指定プレイヤーが操縦中か */
    boolean isPilotedBy(ServerPlayer player);

    /** 操縦者UUIDを取得 */
    UUID getPilotUUID();

    /** 操縦開始 (成功時 true) */
    boolean startPiloting(ServerPlayer player);

    /** 操縦終了 */
    void stopPiloting(ServerPlayer player);

    /** エンティティとしての自身を取得 */
    default Entity asEntity() {
        return (Entity) this;
    }
}

