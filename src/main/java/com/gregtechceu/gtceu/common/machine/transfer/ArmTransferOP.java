package com.gregtechceu.gtceu.common.machine.transfer;

import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import lombok.*;
import lombok.experimental.Accessors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import javax.annotation.ParametersAreNonnullByDefault;

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
    @NonNull
    private ItemFilter filter = ItemFilter.EMPTY;

    public ArmTransferOP(BlockPosFace from, BlockPosFace to, ItemFilter filter) {
        this.from = from;
        this.to = to;
        this.filter = filter;
    }

    public static ArmTransferOP of(BlockPosFace from, BlockPosFace to, ItemFilter filter) {
        return new ArmTransferOP(from, to, filter);
    }

    public static ArmTransferOP of(BlockPosFace from, BlockPosFace to) {
        return new ArmTransferOP(from, to, ItemFilter.EMPTY);
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

        // TODO filter

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        var from = nbt.getCompound("from");
        this.from = new BlockPosFace(NbtUtils.readBlockPos(from.getCompound("pos")), Direction.from3DDataValue(from.getByte("face")));

        var to = nbt.getCompound("to");
        this.to = new BlockPosFace(NbtUtils.readBlockPos(to.getCompound("pos")), Direction.from3DDataValue(to.getByte("face")));

        // TODO filter
    }
}
