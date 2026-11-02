package com.microdrone.mod.client.controller;

/**
 * プレイヤーのキーボード入力のスナップショット (-1.0 ~ 1.0)
 */
public record InputSnapshot(float forward, float strafe, float vertical) {
}
