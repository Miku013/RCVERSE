package com.microdrone.mod.client.renderer.item;

import com.microdrone.mod.client.model.DroneBatteryModel;
import com.microdrone.mod.item.DroneBatteryItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class DroneBatteryItemRenderer extends GeoItemRenderer<DroneBatteryItem> {
    public DroneBatteryItemRenderer() {
        super(new DroneBatteryModel());
    }
}

