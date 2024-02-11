package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.common.data.GTMachines;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.HitResult;

public class RobotArmBehaviour implements IInteractionItem {
    private int tier;

    public RobotArmBehaviour(int tier) {
        this.tier = tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var hitResult = context.getHitResult();
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            var blockPos = hitResult.getBlockPos();
            var direction = hitResult.getDirection();
            var world = context.getLevel();
            var pos = blockPos.relative(direction);
            if (world.getBlockState(pos).isAir()) {
                if (!world.isClientSide()) {
                    world.setBlockAndUpdate(pos, GTMachines.ROBOT_ARM.defaultBlockState());
                }
                context.getItemInHand().shrink(1);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}
