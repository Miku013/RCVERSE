package com.microdrone.mod.sound;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MicroDroneMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> TANK_CANNON_FIRE =
            SOUND_EVENTS.register("tank_cannon_fire",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "tank_cannon_fire")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CHECKPOINT_PASS =
            SOUND_EVENTS.register("checkpoint_pass",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "checkpoint_pass")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RC_CAR_DRIVE =
            SOUND_EVENTS.register("rc_car_drive",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "rc_car_drive")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RC_CAR_SKID =
            SOUND_EVENTS.register("rc_car_skid",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "rc_car_skid")));

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}

