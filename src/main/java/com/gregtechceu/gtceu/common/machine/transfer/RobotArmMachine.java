package com.gregtechceu.gtceu.common.machine.transfer;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.common.machine.trait.ArmTransferExecutor;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RPCMethod;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.utils.interpolate.Eases;
import com.lowdragmc.lowdraglib.utils.interpolate.IEase;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RobotArmMachine extends TieredMachine implements IFancyUIMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(RobotArmMachine.class, MetaMachine.MANAGED_FIELD_HOLDER);

    @Getter
    @Persisted
    @DescSynced
    protected final ArmTransferExecutor executor;
    // runtime
    private int animationTick, animationDuration;
    private Vector4f lastArmRotation, nextArmRotation;
    private float lastClampRotation, nextClampRotation;
    @Nullable
    private Runnable onAnimationEnd;
    @Nullable
    @Getter
    private ItemStack[] transferredItems;

    public RobotArmMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, 0); // TODO tier
        this.executor = createExecutor(args);
        this.animationTick = 0;
        this.animationDuration = 0;
        this.lastArmRotation = new Vector4f(0, 0, 0, 0);
        this.nextArmRotation = new Vector4f(0, 0, 0, 0);
        this.lastClampRotation = 0;
        this.nextClampRotation = 0;
        this.onAnimationEnd = null;
    }

    //////////////////////////////////////
    //*****     Initialization    ******//
    //////////////////////////////////////
    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public ArmTransferExecutor createExecutor(Object... args) {
        return new ArmTransferExecutor(this);
    }

    public int getMaxOpCount() {
        return 8;
    }

    /**
     * Get the cool down time of the arm.
     * @return duration before each action (animation) in ticks.
     */
    public int getCoolDown() {
        return 12 * 4;
    }

    /**
     * Get the max transfer amount per action.
     */
    public int getMaxTransferAmount() {
        return 64;
    }

    /**
     * Get the radius of the arm.
     */
    public int getRadius() {
        return 5;
    }

    public IEase getAnimationEase() {
        return Eases.EaseQuadInOut;
    }

    public int getTransferDuration() {
        return getCoolDown();
    }

    //////////////////////////////////////
    //*****       Animation       ******//
    //////////////////////////////////////

    @RPCMethod
    @SuppressWarnings("unused")
    public void transferAnimation(ArmTransferOP op, Tag transferredItems) {
        // play animation
        if (isRemote()) {
            var transferDuration = getTransferDuration();
            var from = new Vector3f(
                    op.from().pos.getX() + 0.5f + op.from().facing.getStepX() * 0.5f,
                    op.from().pos.getY() + 0.5f + op.from().facing.getStepY() * 0.5f,
                    op.from().pos.getZ() + 0.5f + op.from().facing.getStepZ() * 0.5f);
            var to = new Vector3f(
                    op.to().pos.getX() + 0.5f + op.to().facing.getStepX() * 0.5f,
                    op.to().pos.getY() + 0.5f + op.to().facing.getStepY() * 0.5f,
                    op.to().pos.getZ() + 0.5f + op.to().facing.getStepZ() * 0.5f);
            var fromRotation = calculateRotation(from);
            var toRotation = calculateRotation(to);
            var centerAxisDegree = (fromRotation.x + toRotation.x) / 2;
            var opposite = Mth.wrapDegrees(centerAxisDegree + 180);
            if (Math.min(Math.abs(fromRotation.x - opposite), Math.abs(toRotation.x - opposite)) < Math.abs(fromRotation.x - centerAxisDegree)) {
                centerAxisDegree = opposite;
            }
            var center = new Vector4f(centerAxisDegree, 0, 0, 0);
            moveTo(fromRotation, transferDuration / 4, () -> {
                if (transferredItems instanceof ListTag listTag) {
                    this.transferredItems = listTag.stream()
                        .filter(CompoundTag.class::isInstance)
                        .map(CompoundTag.class::cast)
                        .map(ItemStack::of)
                        .toArray(ItemStack[]::new);
                }
                moveTo(center, transferDuration / 4, () ->
                    moveTo(toRotation, transferDuration / 4, () -> {
                        this.transferredItems = null;
                        moveTo(new Vector4f(toRotation.x, 0, 0, 0), transferDuration / 4);
                    }));
            });

        } else {
            rpcToTracking("transferAnimation", op, transferredItems);
        }
    }

    /**
     * Get the rotation of the arm.
     * @return (axisDegree, arm1Degree, arm2Degree, arm3Degree) the rotation of the arm in degree
     */
    public Vector4f getArmRotation(float partialTicks) {
        var delta = animationDuration == 0 ? 1 : Math.min((animationTick + partialTicks) / animationDuration, 1);
        // apply EaseQuadInOut
        return interpolateRotation(lastArmRotation, nextArmRotation, getAnimationEase().getInterpolation(delta));
    }

    /**
     * Get the rotation of the arm clamp.
     * @return the rotation of the arm clamp in degree
     */
    public float getClampRotation(float partialTicks) {
        // TODO clamp rotation
        return 0;
//        var delta = animationDuration == 0 ? 1 : Math.min((animationTick + partialTicks) / animationDuration, 1);
//        var wrapped = wrapDegreesInRange(lastClampRotation, nextClampRotation, -180, 180);
//        return Mth.lerp(delta, wrapped[0], wrapped[1]);
    }

    public void moveTo(Vector4f rotation, int duration, @Nullable Runnable onAnimationEnd) {
        this.lastArmRotation = new Vector4f(getArmRotation(0));
        this.nextArmRotation = new Vector4f(rotation);
        this.animationTick = 0;
        this.animationDuration = duration;
        this.onAnimationEnd = onAnimationEnd;
    }

    public void moveTo(Vector3f dest, int duration, @Nullable Runnable onAnimationEnd) {
        this.moveTo(calculateRotation(dest), duration, onAnimationEnd);
    }

    public void moveTo(Vector3f dest, int duration) {
        moveTo(dest, duration, null);
    }

    public void moveTo(Vector4f rotation, int duration) {
        moveTo(rotation, duration, null);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void clientTick() {
        super.clientTick();
        if (animationTick < animationDuration) {
            animationTick++;
            if (animationTick == animationDuration) {
                if (onAnimationEnd != null) onAnimationEnd.run();
            }
        }
    }

    private float[] wrapDegreesInRange(float from, float to, float minDegree, float maxDegree) {
        // range (minDegree, maxDegree)
        if (from < minDegree) from += 360;
        if (to < minDegree) to += 360;
        if (from > maxDegree) from -= 360;
        if (to > maxDegree) to -= 360;
        return new float[] {from, to};
    }

    public Vector4f interpolateRotation(Vector4f from, Vector4f to, float delta) {
        from = new Vector4f(from);
        to = new Vector4f(to);

        // axis Y rotation should follow the shortest path
        if (Math.abs(from.x - to.x) > 180) {
            if (from.x > to.x) to.x += 360;
            else from.x += 360;
        }

        // arm1 rotation range (0, 360)
        var wrapped = wrapDegreesInRange(from.y, to.y, 0, 360);
        from.y = wrapped[0];
        to.y = wrapped[1];
        // arm2 rotation range (-270, 90)
        wrapped = wrapDegreesInRange(from.z, to.z, -270f, 90);
        from.z = wrapped[0];
        to.z = wrapped[1];
        // arm3 rotation range (-270, 90)
        wrapped = wrapDegreesInRange(from.w, to.w, -270f, 90);
        from.w = wrapped[0];

        return new Vector4f(
                Mth.wrapDegrees(Mth.lerp(delta, from.x, to.x)),
                Mth.lerp(delta, from.y, to.y),
                Mth.lerp(delta, from.z, to.z),
                Mth.lerp(delta, from.w, to.w)
        );
    }

    public Vector4f calculateRotation(Vector3f dest) {
        var center = new Vector3f(getPos().getX() + 0.5f, getPos().getY() + 5 / 16f, getPos().getZ() + 0.5f);
        var vec = new Vector3f(dest).sub(center);
        float axisAngle, arm1Angle, arm2Angle, arm3Angle;
        // calculate axisAngle
        if (vec.x == 0 && vec.z == 0) {
            axisAngle = getArmRotation(0).x();
        } else {
            axisAngle = new Vector3f(vec.x, 0, vec.z).angle(new Vector3f(0, 0, 1)); // radians
        }
        if (vec.x < 0) axisAngle = Mth.TWO_PI - axisAngle;
        var toFrom = new Vector3f(0, 5 / 16f, 0.5f).rotateAxis(axisAngle, 0, 1, 0);
        var from = new Vector3f(toFrom).add(getPos().getX() + 0.5f, getPos().getY(), getPos().getZ() + 0.5f);
        var rd = dest.distance(from);
        var rv = new Vector3f(dest).sub(from);
        var a0 = rv.angle(new Vector3f(toFrom.x, 0, toFrom.z));
        if (rv.y < 0) a0 = -a0;

        // calculate arm1Angle, arm2Angle, arm3Angle, arm4Angle
        rd = Math.min(rd, 3);
        float a1;
        if (rd > 1) {
            a1 = (float) Math.acos((rd - 1) / 2);
            arm1Angle = Mth.PI - a0 - a1;
            arm2Angle = -(Mth.HALF_PI - a1);
        } else {
            a1 = (float) Math.asin((1 - rd) / 2);
            arm1Angle = Mth.HALF_PI - a0 - a1;
            arm2Angle = a1;
        }
        arm3Angle = arm2Angle;

        return new Vector4f(
                (float) Mth.wrapDegrees(Math.toDegrees(axisAngle)),
                (float) Mth.wrapDegrees(Math.toDegrees(arm1Angle)),
                (float) Mth.wrapDegrees(Math.toDegrees(arm2Angle)),
                (float) Mth.wrapDegrees(Math.toDegrees(arm3Angle)));
    }

    //////////////////////////////////////
    //*****          GUI          ******//
    //////////////////////////////////////

    @Override
    public Widget createUIWidget() {
        return executor.createUIWidget();
    }
}
