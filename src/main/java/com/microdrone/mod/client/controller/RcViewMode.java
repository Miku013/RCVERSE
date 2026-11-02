package com.microdrone.mod.client.controller;

/**
 * すべてのRC機体（ドローン、カー、戦車、プレーン）で共通の3視点モード列挙型
 */
public enum RcViewMode {
    /** 地上プロポ視点（プロポを持ったプレイヤー視点、機体を自動追尾または自由視線で見上げる） */
    GROUND,

    /** 機体三人称チェイス視点（機体後方上空からの追従） */
    CHASE,

    /** 一人称搭乗視点（ドローンFPV、カー車載、戦車ガンナーFPS、プレーンコックピット） */
    FIRST_PERSON
}
