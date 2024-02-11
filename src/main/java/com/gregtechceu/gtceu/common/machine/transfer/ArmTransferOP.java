package com.gregtechceu.gtceu.common.machine.transfer;

import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

/**
 * Operation of arm transferring.
 */
@NoArgsConstructor
@Accessors(fluent = true)
@ParametersAreNonnullByDefault
public class ArmTransferOP implements ITagSerializable<CompoundTag> {
    @Getter
    @NonNull
    private BlockPosFace from = new BlockPosFace(BlockPos.ZERO, Direction.UP);
    @Getter
    @NonNull
    private BlockPosFace to = new BlockPosFace(BlockPos.ZERO, Direction.DOWN);
    @Getter
    protected ItemStack filterItem = ItemStack.EMPTY;
    @Getter
    protected int transferAmount = -1;
    // runtime
    @Nullable
    private ItemFilter filter = null;

    @Builder(toBuilder = true)
    public ArmTransferOP(BlockPosFace from, BlockPosFace to, ItemStack filterItem, int transferAmount) {
        this.from = from;
        this.to = to;
        this.filterItem = filterItem;
        this.transferAmount = transferAmount;
    }

    public ItemFilter getFilter() {
        if (this.filter == null) {
            this.filter = ItemFilter.loadFilter(filterItem);
        }
        return filter;
    }

    public static ArmTransferOP of(BlockPosFace from, BlockPosFace to, ItemStack filterItem, int transferAmount) {
        return new ArmTransferOP(from, to, filterItem, transferAmount);
    }

    public static ArmTransferOP of(BlockPosFace from, BlockPosFace to) {
        return new ArmTransferOP(from, to, ItemStack.EMPTY, -1);
    }

    public static ArmTransferOP of(CompoundTag tag) {
        var op = new ArmTransferOP();
        op.deserializeNBT(tag);
        return op;
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        var from = new CompoundTag();
        from.put("pos", NbtUtils.writeBlockPos(this.from.pos));
        from.putByte("face", (byte) this.from.facing.get3DDataValue());
        tag.put("from", from);
        var to = new CompoundTag();
        to.put("pos", NbtUtils.writeBlockPos(this.to.pos));
        to.putByte("face", (byte) this.to.facing.get3DDataValue());
        tag.put("to", to);
        tag.put("filter", this.filterItem.serializeNBT());
        tag.putInt("transferAmount", this.transferAmount);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        var from = nbt.getCompound("from");
        this.from = new BlockPosFace(NbtUtils.readBlockPos(from.getCompound("pos")), Direction.from3DDataValue(from.getByte("face")));
        var to = nbt.getCompound("to");
        this.to = new BlockPosFace(NbtUtils.readBlockPos(to.getCompound("pos")), Direction.from3DDataValue(to.getByte("face")));
        this.filterItem = ItemStack.of(nbt.getCompound("filter"));
        this.transferAmount = nbt.getInt("transferAmount");
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.from, this.to, this.filterItem);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ArmTransferOP op) {
            return this.from.equals(op.from) && this.to.equals(op.to) && ItemStack.isSameItemSameTags(this.filterItem, op.filterItem);
        }
        return super.equals(obj);
    }
}
