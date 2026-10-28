package com.microdrone.mod.client.renderer.item;

import com.microdrone.mod.client.model.DroneControllerModel;
import com.microdrone.mod.item.DroneControllerItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class DroneControllerItemRenderer extends GeoItemRenderer<DroneControllerItem> {
    public DroneControllerItemRenderer() {
        super(new DroneControllerModel());
    }
}

