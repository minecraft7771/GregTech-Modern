package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.common.cover.data.DistributionMode;
import com.gregtechceu.gtceu.common.cover.data.TransferMode;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.machine.trait.ArmTransferExecutor;
import com.gregtechceu.gtceu.common.machine.transfer.ArmTransferOP;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

public class ArmTransferExecutorConsole extends WidgetGroup {
    private final ArmTransferExecutor executor;
    private SceneWidget scene;
    private DraggableScrollableWidgetGroup opList;
    // runtime
    private BlockPosFace selectedPosFace;
    private int selectedIndex = -1;
    private final List<ArmTransferOP> lastOps = new ArrayList<>();
    private final List<Integer> lastQueue = new ArrayList<>();

    public ArmTransferExecutorConsole(ArmTransferExecutor executor) {
        super(0, 0, 168, 188);
        this.executor = executor;
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        lastOps.clear();
        lastOps.addAll(this.executor.getOps());
        lastQueue.clear();
        lastQueue.addAll(this.executor.getQueue());
        writeOps(buffer);
        reloadOpList();
        writeQueue(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        readOps(buffer);
        reloadOpList();
        readQueue(buffer);
    }

    public void writeOps(FriendlyByteBuf buffer) {
        buffer.writeVarInt(lastOps.size());
        for (var op : lastOps) {
            buffer.writeNbt(op.serializeNBT());
        }
    }

    public void readOps(FriendlyByteBuf buffer) {
        var size = buffer.readVarInt();
        lastOps.clear();
        for (int i = 0; i < size; i++) {
            var tag = buffer.readNbt();
            if (tag != null) {
                lastOps.add(ArmTransferOP.of(tag));
            }
        }
    }

    public void writeQueue(FriendlyByteBuf buffer) {
        buffer.writeVarInt(lastQueue.size());
        for (var op : lastQueue) {
            buffer.writeVarInt(op);
        }
    }

    public void readQueue(FriendlyByteBuf buffer) {
        var size = buffer.readVarInt();
        lastQueue.clear();
        for (int i = 0; i < size; i++) {
            lastQueue.add(buffer.readVarInt());
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        var latestOps = executor.getOps();
        if (lastOps.size() != latestOps.size() || !lastOps.equals(latestOps)) {
            lastOps.clear();;
            lastOps.addAll(latestOps);
            writeUpdateInfo(0, this::writeOps);
            reloadOpList();
        }
        var latestQueue = executor.getQueue();
        if (lastQueue.size() != latestQueue.size() || !lastQueue.equals(latestQueue)) {
            lastQueue.clear();
            lastQueue.addAll(latestQueue);
            writeUpdateInfo(-1, this::writeQueue);
        }
    }

    @Nullable
    private ArmTransferOP getSelected() {
        if (selectedIndex < 0 || selectedIndex >= lastOps.size()) {
            return null;
        }
        return lastOps.get(selectedIndex);
    }

    @Override
    public void initWidget() {
        super.initWidget();
        // scene
        scene = new SceneWidget(2, 2, getSize().width - 4, 100 - 4, gui.entityPlayer.level())
            .setOnSelected((pos, facing) -> this.selectedPosFace = new BlockPosFace(pos, facing))
            .setRenderSelect(false);
        if (isRemote()) {
            scene.setRenderedCore(getAroundBlocks(), null);
            scene.getRenderer().setAfterWorldRender(this::renderBlockOverLay);
            var playerRotation = gui.entityPlayer.getRotationVector();
            scene.setCameraYawAndPitch(playerRotation.x, playerRotation.y - 90);
        }
        addWidget(scene.setBackground(new GuiTextureGroup(ColorPattern.BLACK.rectTexture(), ColorPattern.GRAY.borderTexture(1))));

        addWidget(new PredicatedButtonWidget(6, 6, 18, 18, new GuiTextureGroup(ColorPattern.T_GRAY.rectTexture(), ColorPattern.GRAY.borderTexture(1), IO.OUT.getIcon()), (cd) -> {
            var selected = getSelected();
            if (selected != null && selectedPosFace != null) {
                var newOP = selected.toBuilder().from(new BlockPosFace(selectedPosFace.pos, selectedPosFace.facing)).build();
                if (!isRemote()) {
                    executor.updateOp(selectedIndex, newOP);
                }
                lastOps.set(selectedIndex, newOP);
            }
        }).setPredicate(() -> getSelected() != null && selectedPosFace != null).setHoverTooltips("gtceu.gui.arm_console.set_as_source"));
        addWidget(new PredicatedButtonWidget(6, 6 + 22, 18, 18, new GuiTextureGroup(ColorPattern.T_GRAY.rectTexture(), ColorPattern.GRAY.borderTexture(1), IO.IN.getIcon()), (cd) -> {
            var selected = getSelected();
            if (selected != null && selectedPosFace != null) {
                var newOP = selected.toBuilder().to(new BlockPosFace(selectedPosFace.pos, selectedPosFace.facing)).build();
                if (!isRemote()) {
                    executor.updateOp(selectedIndex, newOP);
                }
                lastOps.set(selectedIndex, newOP);
            }
        }).setPredicate(() -> getSelected() != null && selectedPosFace != null).setHoverTooltips("gtceu.gui.arm_console.set_as_target"));

        // buttons
        addWidget(new ToggleButtonWidget(2, 100, 18, 18, TransferMode.TRANSFER_EXACT.getIcon(), executor::isBlockMode, executor::setBlockMode)
            .setShouldUseBaseBackground()
            .setTooltipText("gtceu.gui.arm_console.block_mode"));

        addWidget(new ToggleButtonWidget(2 + 20, 100, 18, 18, DistributionMode.ROUND_ROBIN_PRIO.getIcon(), executor::isRandomMode, executor::setRandomMode)
            .setShouldUseBaseBackground()
            .setTooltipText("gtceu.gui.arm_console.random_mode"));

        addWidget(new ToggleButtonWidget(2 + 40, 100, 18, 18, Icons.DELETE.copy().scale(0.85f), executor::isResetMode, executor::setResetMode)
            .setShouldUseBaseBackground()
            .setTooltipText("gtceu.gui.arm_console.reset_mode"));


        // add
        addWidget(new PredicatedButtonWidget(getSize().width - 20 * 4, 100, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, Icons.ADD.copy().scale(0.85f)), (cd) -> {
            if (!isRemote() && lastOps.size() < executor.getMaxOpCount()) {
                executor.addOp(ArmTransferOP.of(new BlockPosFace(executor.getMachine().getPos(), Direction.UP), new BlockPosFace(executor.getMachine().getPos(), Direction.DOWN)));
            }
        }).setPredicate(() -> lastOps.size() < executor.getMaxOpCount()).setHoverTooltips("gtceu.gui.arm_console.add_op"));
        // remove
        addWidget(new PredicatedButtonWidget(getSize().width - 20 * 3, 100, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, Icons.REMOVE.copy().scale(0.85f)), (cd) -> {
            if (!isRemote()) {
                var selected = getSelected();
                if (selected != null) {
                    executor.removeOp(selectedIndex);
                }
            }
            selectedIndex = -1;
        }).setPredicate(() -> getSelected() != null).setHoverTooltips("gtceu.gui.arm_console.remove_op"));
        // move up
        addWidget(new PredicatedButtonWidget(getSize().width - 20 * 2, 100, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, Icons.UP.copy().scale(0.85f)), (cd) -> {
            if (selectedIndex > 0 && selectedIndex < lastOps.size()) {
                var last = lastOps.get(selectedIndex - 1);
                var selected = getSelected();
                if (!isRemote()) {
                    executor.addOp(selectedIndex - 1, selected);
                    executor.removeOp(selectedIndex + 1);
                }
                lastOps.set(selectedIndex, last);
                lastOps.set(selectedIndex - 1, selected);
                var selectedGroup = (SelectableWidgetGroup) opList.widgets.get(selectedIndex);
                var lastGroup = (SelectableWidgetGroup) opList.widgets.get(selectedIndex - 1);
                selectedGroup.addSelfPosition(0, -16);
                lastGroup.addSelfPosition(0, 16);
                opList.widgets.set(selectedIndex, lastGroup);
                opList.widgets.set(selectedIndex - 1, selectedGroup);
                selectedIndex--;
            }
        }).setPredicate(() -> selectedIndex > 0 && selectedIndex < lastOps.size()).setHoverTooltips("gtceu.gui.arm_console.move_up"));
        // move down
        addWidget(new PredicatedButtonWidget(getSize().width - 20, 100, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, Icons.DOWN.copy().scale(0.85f)), (cd) -> {
            if (selectedIndex < lastOps.size() - 1 && selectedIndex >= 0) {
                var last = lastOps.get(selectedIndex + 1);
                var selected = getSelected();
                if (!isRemote()) {
                    executor.addOp(selectedIndex + 2, selected);
                    executor.removeOp(selectedIndex);
                }
                lastOps.set(selectedIndex, last);
                lastOps.set(selectedIndex + 1, selected);
                var selectedGroup = (SelectableWidgetGroup) opList.widgets.get(selectedIndex);
                var lastGroup = (SelectableWidgetGroup) opList.widgets.get(selectedIndex + 1);
                selectedGroup.addSelfPosition(0, 16);
                lastGroup.addSelfPosition(0, -16);
                opList.widgets.set(selectedIndex, lastGroup);
                opList.widgets.set(selectedIndex + 1, selectedGroup);
                selectedIndex++;
            }
        }).setPredicate(() -> selectedIndex < lastOps.size() - 1 && selectedIndex >= 0).setHoverTooltips("gtceu.gui.arm_console.move_down"));

        // op list
        opList = new DraggableScrollableWidgetGroup(2, 120, getSize().width - 4, getSize().height - 20 - 120);
        opList.setBackground(new GuiTextureGroup(ColorPattern.BLACK.rectTexture(), ColorPattern.GRAY.borderTexture(1)));
        opList.setYScrollBarWidth(12);
        opList.setYBarStyle(GuiTextures.SLIDER_BACKGROUND_VERTICAL, GuiTextures.BUTTON);
        addWidget(opList);

        // op queue
        int y = getSize().height - 20 + 6;
        addWidget(new ImageWidget(2, y + 2, 16, 16, new ItemStackTexture(GTItems.ROBOT_ARM_LV.asItem())).setHoverTooltips("gtceu.gui.arm_console.queue"));
        addWidget(new ButtonWidget(20, y, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, Icons.REPLAY.copy().scale(0.85f)), (cd) -> {
            if (!isRemote()) {
                executor.reset();
            }
        }).setHoverTooltips("gtceu.gui.arm_console.reset_queue"));
        DraggableScrollableWidgetGroup queueList;
        addWidget(queueList = new DraggableScrollableWidgetGroup(38, y, getSize().width - 4 - 40, 18));
        for (int i = 0; i < 7; i++) {
            int finalI = i;
            queueList.addWidget(new ImageWidget(i * 18, 0, 18, 18,
                new GuiTextureGroup(GuiTextures.SLOT, new TextTexture("")
                    .setSupplier(() -> lastQueue.size() > finalI ? lastQueue.get(finalI).toString() : ""))));
        }
    }

    private void reloadOpList() {
        opList.clearAllWidgets();
        int y = 0;
        int width = opList.getSize().width - 12;
        int height = 18;
        for (int i = 0; i < lastOps.size(); i++) {
            var wrapper = new SelectableWidgetGroup(0, y, width, 18);
            IntSupplier indexGetter = () -> opList.widgets.indexOf(wrapper);
            wrapper.addWidget(new ImageWidget(0, 2, 16, 16, new TextTexture("")
                .setSupplier(() -> String.valueOf(indexGetter.getAsInt()))));
            wrapper.addWidget(new ImageWidget(18, 1, 16, 16, () -> {
                if (indexGetter.getAsInt() == -1) return IGuiTexture.EMPTY;
                var op = lastOps.get(indexGetter.getAsInt());
                return new ItemStackTexture(getOpItem(op.from().pos));
            }));
            wrapper.addWidget(new ImageWidget(18 * 2, 1, 16, 16, GuiTextures.BUTTON_RIGHT));
            wrapper.addWidget(new ImageWidget(18 * 3, 1, 16, 16, () -> {
                if (indexGetter.getAsInt() == -1) return IGuiTexture.EMPTY;
                var op = lastOps.get(indexGetter.getAsInt());
                return new ItemStackTexture(getOpItem(op.to().pos));
            }));
            wrapper.addWidget(new TextFieldWidget(width - 18 - 32, 1, 30, 16,
                () -> lastOps.get(indexGetter.getAsInt()).transferAmount() + "",
                value -> {
                    var index = indexGetter.getAsInt();
                    var newOp = lastOps.get(index).toBuilder().transferAmount(Integer.parseInt(value)).build();
                    if (!isRemote()) {
                        executor.updateOp(index, newOp);
                    }
                    lastOps.set(index, newOp);
                })
                .setNumbersOnly(-1, executor.getMaxTransferAmount())
                .setHoverTooltips(LangHandler.getMultiLang("gtceu.gui.arm_console.transfer_amount").toArray(new Component[0])));
            var inventory = new ItemStackTransfer(lastOps.get(i).filterItem());
            inventory.setFilter(stack -> ItemFilter.FILTERS.containsKey(stack.getItem()));
            wrapper.addWidget(new SlotWidget(inventory, 0, width - 18, 0)
                .setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT, GuiTextures.FILTER_SLOT_OVERLAY))
                .setChangeListener(() -> {
                    var index = indexGetter.getAsInt();
                    var newOp = lastOps.get(index).toBuilder().filterItem(inventory.getStackInSlot(0)).build();
                    if (!isRemote()) {
                        executor.updateOp(index, newOp);
                    }
                    lastOps.set(index, newOp);
                }));
            wrapper.setBackground(ColorPattern.T_BLACK.rectTexture());
            int finalI = i;
            wrapper.setOnSelected(s -> {
                s.setBackground(ColorPattern.T_WHITE.rectTexture());
                selectedIndex = finalI;
                selectedPosFace = null;
                writeClientAction(0, buf -> buf.writeVarInt(selectedIndex));
            });
            wrapper.setOnUnSelected(s -> s.setBackground(ColorPattern.T_BLACK.rectTexture()));
            opList.addWidget(wrapper);
            y += height;
        }
    }

    private ItemStack getOpItem(BlockPos pos) {
        if (pos == null || pos.equals(executor.getMachine().getPos())) {
            return new ItemStack(Items.BARRIER);
        }
        var item = executor.getMachine().getLevel().getBlockState(pos).getBlock().asItem();
        return new ItemStack(item);
    }

    private List<BlockPos> getAroundBlocks() {
        var machine = executor.getMachine();
        var radius = machine.getRadius();
        var radiusSq = radius * radius;
        var blocks = new ArrayList<BlockPos>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    var pos = machine.getPos().offset(x, y, z);
                    if (pos.equals(machine.getPos())) {
                        continue;
                    }
                    if (machine.getPos().distSqr(pos) <= radiusSq) {
                        if (Optional.ofNullable(machine.getLevel().getBlockEntity(pos)).flatMap(e -> e.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve()).isPresent()) {
                            blocks.add(pos);
                        }
                    }
                }
            }
        }
        return blocks;
    }

    @OnlyIn(Dist.CLIENT)
    public void renderBlockOverLay(WorldSceneRenderer renderer) {
        scene.renderBlockOverLay(renderer);
        var selected = getSelected();
        if (selected != null) {
            var from = selected.from();
            var to = selected.to();
            if (!from.pos.equals(executor.getMachine().getPos())) {
                scene.drawFacingBorder(new PoseStack(), from, 0xffDF7126, 1);
            }
            if (!to.pos.equals(executor.getMachine().getPos())) {
                scene.drawFacingBorder(new PoseStack(), to, 0xff639BFF, 1);
            }
        }
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == 0) {
            selectedIndex = buffer.readVarInt();
        } else {
            super.handleClientAction(id, buffer);
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == 0) {
            readOps(buffer);
            reloadOpList();
        } else if (id == -1) {
            readQueue(buffer);
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }
}