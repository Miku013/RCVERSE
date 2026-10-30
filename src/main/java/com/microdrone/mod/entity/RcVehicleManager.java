package com.microdrone.mod.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 操縦中の全RC機体（ドローン、RCカー、戦車等）を一元管理するマネージャー
 */
public class RcVehicleManager {
    // 操縦中プレイヤーUUID -> 操縦中RC機体
    private static final Map<UUID, RcControllable> ACTIVE_PILOTED_VEHICLES = new ConcurrentHashMap<>();
    // 身代わりダミー人形UUID -> 操縦中RC機体
    private static final Map<UUID, RcControllable> DUMMY_TO_VEHICLE = new ConcurrentHashMap<>();

    public static void registerPiloting(UUID pilotUUID, RcControllable vehicle) {
        if (pilotUUID != null && vehicle != null) {
            ACTIVE_PILOTED_VEHICLES.put(pilotUUID, vehicle);
        }
    }

    public static void unregisterPiloting(UUID pilotUUID) {
        if (pilotUUID != null) {
            ACTIVE_PILOTED_VEHICLES.remove(pilotUUID);
        }
    }

    public static RcControllable getByPilot(UUID pilotUUID) {
        if (pilotUUID == null) return null;
        return ACTIVE_PILOTED_VEHICLES.get(pilotUUID);
    }

    public static void registerDummy(UUID dummyUUID, RcControllable vehicle) {
        if (dummyUUID != null && vehicle != null) {
            DUMMY_TO_VEHICLE.put(dummyUUID, vehicle);
        }
    }

    public static void unregisterDummy(UUID dummyUUID) {
        if (dummyUUID != null) {
            DUMMY_TO_VEHICLE.remove(dummyUUID);
        }
    }

    public static RcControllable getByDummy(UUID dummyUUID) {
        if (dummyUUID == null) return null;
        return DUMMY_TO_VEHICLE.get(dummyUUID);
    }
}

