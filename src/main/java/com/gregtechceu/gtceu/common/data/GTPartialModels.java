package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.client.renderer.machine.RobotArmRenderer;
import com.jozufozu.flywheel.core.PartialModel;

public class GTPartialModels {
    public static final PartialModel AXIS_Y = new PartialModel(RobotArmRenderer.AXIS_Y);
    public static final PartialModel ARM_1 = new PartialModel(RobotArmRenderer.ARM_1);
    public static final PartialModel ARM_2 = new PartialModel(RobotArmRenderer.ARM_2);
    public static final PartialModel ARM_3 = new PartialModel(RobotArmRenderer.ARM_3);

    public static void init() {

    }
}
