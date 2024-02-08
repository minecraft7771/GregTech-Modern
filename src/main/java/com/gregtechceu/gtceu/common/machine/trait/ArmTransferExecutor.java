package com.gregtechceu.gtceu.common.machine.trait;

import com.google.common.collect.ImmutableList;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.gui.widget.ArmTransferExecutorConsole;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.common.machine.transfer.ArmTransferOP;
import com.gregtechceu.gtceu.common.machine.transfer.RobotArmMachine;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.side.item.ItemTransferHelper;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArmTransferExecutor extends MachineTrait {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ArmTransferExecutor.class);
    @Override
    public ManagedFieldHolder getFieldHolder() { return MANAGED_FIELD_HOLDER; }

    public enum State {
        IDLE,
        SEARCHING,
        TRANSFERRING,
        COOL_DOWN,
    }
    private final ConditionalSubscriptionHandler executorSubscription;
    @Getter
    @Persisted
    private boolean blockMode;
    @Getter
    @Persisted
    private boolean randomMode;
    @Getter
    @Persisted
    private boolean queueMode;
    @Persisted
    private final List<ArmTransferOP> opList = new ArrayList<>();
    @Persisted
    private int currentOp = -1; // index of current op, -1 means no op
    @Getter @Setter
    @Persisted
    private State state = State.IDLE;
    @Persisted
    private final List<Integer> opQueue = new ArrayList<>();
    // runtime
    private int cooldown = 0;

    public ArmTransferExecutor(RobotArmMachine armMachine) {
        super(armMachine);
        this.executorSubscription = new ConditionalSubscriptionHandler(armMachine, this::execute, () -> !this.opList.isEmpty());
        opList.add(new ArmTransferOP(new BlockPosFace(armMachine.getPos(), Direction.UP), new BlockPosFace(armMachine.getPos(), Direction.DOWN), ItemFilter.EMPTY));
    }

    @Override
    public RobotArmMachine getMachine() {
        return (RobotArmMachine) super.getMachine();
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        this.executorSubscription.initialize(getMachine().getLevel());
    }

    /**
     * Reset the executor to initial state.
     */
    public void reset() {
        currentOp = -1;
        cooldown = 0;
        opQueue.clear();
        setState(State.IDLE);
    }

    public int getMaxOpCount() {
        return getMachine().getMaxOpCount();
    }

    public void setBlockMode(boolean blockMode) {
        this.blockMode = blockMode;
        reset();
    }

    public void setRandomMode(boolean randomMode) {
        this.randomMode = randomMode;
        reset();
    }

    public void setQueueMode(boolean queueMode) {
        this.queueMode = queueMode;
        reset();
    }

    public ImmutableList<ArmTransferOP> getOps() {
        return ImmutableList.copyOf(opList);
    }

    public void addOp(ArmTransferOP op, int index) {
        opList.add(index, op);
        executorSubscription.updateSubscription();
        reset();
    }

    public void addOp(ArmTransferOP op) {
        addOp(op, opList.size());
    }

    public void removeOp(int index) {
        if (index >= 0 && index < opList.size()) {
            opList.remove(index);
            executorSubscription.updateSubscription();
            reset();
        }
    }

    public void updateOp(int index, ArmTransferOP op) {
        if (index >= 0 && index < opList.size()) {
            opList.set(index, op);
            executorSubscription.updateSubscription();
            reset();
        }
    }

    @Nullable
    public ArmTransferOP getCurrentOp() {
        if (currentOp < 0 || currentOp >= opList.size()) {
            return null;
        }
        return opList.get(currentOp);
    }

    private void execute() {
        executorSubscription.updateSubscription();
        var lastState = getState();
        if (lastState == State.IDLE) {
            if (opList.isEmpty()) return;
            // prepare queue
            opQueue.clear();
            for (int i = 0; i < opList.size(); i++) {
                opQueue.add(i);
            }
            if (isRandomMode()) {
                Collections.shuffle(opQueue);
            }
            setState(State.SEARCHING);
        } else if (lastState == State.SEARCHING) {
            while (!opQueue.isEmpty()) {
                if (currentOp == -1) {
                    currentOp = opQueue.remove(0);
                }
                var op = getCurrentOp();
                if (op == null) {
                    reset();
                    return;
                }
                if (checkOpAvailable(op)) {
                    // if op is available, execute it
                    setState(State.TRANSFERRING);
                    return;
                } else {
                    if (isBlockMode()) {
                        // if it is block mode, wait for next tick
                        return;
                    } else {
                        currentOp = -1;
                    }
                }
            }
            setState(State.IDLE);
        } else if (lastState == State.TRANSFERRING) {
            // transferring
            var op = getCurrentOp();
            if (op == null) {
                reset();
                return;
            }

            var transferredItems = doTransfer(op, getMachine().getMaxTransferAmount());
            if (transferredItems.length == 0) {
                // it should not happen unless the source or dest is changed.
                // in this case, we just try to schedule searching again.
                setState(State.SEARCHING);
            } else {
                // tell client to play animation
                var list = new ListTag();
                for (var item : transferredItems) {
                    list.add(item.save(new CompoundTag()));
                }
                getMachine().transferAnimation(op, list);
                cooldown = getMachine().getCoolDown();
                setState(State.COOL_DOWN);
            }
        } else if (lastState == State.COOL_DOWN) {
            // if we have transferred something, we should wait a while for animation
            if (cooldown <= 0) {
                cooldown = 0;
                currentOp = -1;
                setState(State.SEARCHING);
            } else {
                cooldown--;
            }
        }
    }

    @NotNull
    private ItemStack[] doTransfer(ArmTransferOP op, int maxTransferAmount) {
        int leftAmount = maxTransferAmount;
        ItemStack[] transferredItems = new ItemStack[0];

        var source = ItemTransferHelper.getItemTransfer(machine.getLevel(), op.from().pos, op.from().facing);
        var dest = ItemTransferHelper.getItemTransfer(machine.getLevel(), op.to().pos, op.to().facing);
        if (source != null && dest != null) {
            for (int slotIndex = 0; slotIndex < source.getSlots(); slotIndex++) {
                var extracted = source.extractItem(slotIndex, leftAmount, true);
                if (!extracted.isEmpty()) {
                    var remained = ItemTransferHelper.insertItem(dest, extracted, true);
                    var expected = extracted.getCount() - remained.getCount();
                    if (expected > 0) {
                        // do transfer
                        remained = ItemTransferHelper.insertItem(dest, source.extractItem(slotIndex, expected, false), false);
                        var transferred = ItemTransferHelper.copyStackWithSize(extracted, expected - remained.getCount());
                        if (!transferred.isEmpty()) {
                            transferredItems = ArrayUtils.add(transferredItems, transferred);
                            leftAmount -= transferred.getCount();
                        }
                        if (!remained.isEmpty()) { // it should not happen!!!!! drop extra items to the ground
                            Block.popResource(machine.getLevel(), op.to().pos, remained);
                        }
                        if (leftAmount == 0) {
                            break;
                        }
                    }
                }
            }
        }
        return transferredItems;
    }


    protected boolean checkOpAvailable(ArmTransferOP op) {
        var source = ItemTransferHelper.getItemTransfer(machine.getLevel(), op.from().pos, op.from().facing);
        var dest = ItemTransferHelper.getItemTransfer(machine.getLevel(), op.to().pos, op.to().facing);
        if (source != null && dest != null) {
            for (int slotIndex = 0; slotIndex < source.getSlots(); slotIndex++) {
                var extracted = source.extractItem(slotIndex, 64, true);
                if (!extracted.isEmpty()) {
                    var remained = ItemTransferHelper.insertItem(dest, extracted, true);
                    if (extracted.getCount() > remained.getCount()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    //////////////////////////////////////
    //*****          GUI          ******//
    //////////////////////////////////////
    public Widget createUIWidget() {
        return new ArmTransferExecutorConsole(this);
    }

}
