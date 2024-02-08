package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.common.machine.trait.ArmTransferExecutor;
import com.gregtechceu.gtceu.common.machine.transfer.ArmTransferOP;
import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ArmTransferExecutorConsole extends WidgetGroup {
    private final ArmTransferExecutor executor;
    private SceneWidget sceneWidget;
    private DraggableScrollableWidgetGroup listWidget;
    // runtime
    private BlockPosFace selectedPosFace;
    private int selectedIndex = -1;
    private final List<ArmTransferOP> lastOps = new ArrayList<>();

    public ArmTransferExecutorConsole(ArmTransferExecutor executor) {
        super(0, 0, 168, 168);
        this.executor = executor;
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        lastOps.clear();
        lastOps.addAll(this.executor.getOps());
        writeOps(buffer);
        initList();
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        readOps(buffer);
        initList();
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

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        var latestOps = executor.getOps();
        if (lastOps.size() != latestOps.size() || !lastOps.equals(latestOps)) {
            lastOps.clear();;
            lastOps.addAll(latestOps);
            writeUpdateInfo(0, this::writeOps);
            initList();
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
        sceneWidget = new SceneWidget(2, 2, getSize().width - 4, 100 - 4, gui.entityPlayer.level())
            .setOnSelected((pos, facing) -> this.selectedPosFace = new BlockPosFace(pos, facing))
            .setRenderSelect(false);
        if (isRemote()) {
            sceneWidget.setRenderedCore(getAroundBlocks(), null);
            sceneWidget.getRenderer().setAfterWorldRender(this::renderBlockOverLay);
            var playerRotation = gui.entityPlayer.getRotationVector();
            sceneWidget.setCameraYawAndPitch(playerRotation.x, playerRotation.y - 90);
        }
        addWidget(sceneWidget.setBackground(new GuiTextureGroup(ColorPattern.BLACK.rectTexture(), ColorPattern.GRAY.borderTexture(1))));

        addWidget(new PredicatedButtonWidget(4, 4, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, IO.OUT.getIcon()), (cd) -> {
            if (!isRemote() && getSelected() != null && selectedPosFace != null) {
                executor.updateOp(selectedIndex, ArmTransferOP.of(new BlockPosFace(selectedPosFace.pos, selectedPosFace.facing), getSelected().to(), getSelected().filter()));
            }
        }).setPredicate(() -> getSelected() != null && selectedPosFace != null));
        addWidget(new PredicatedButtonWidget(4, 4 + 20, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, IO.IN.getIcon()), (cd) -> {
            if (!isRemote() && getSelected() != null && selectedPosFace != null) {
                executor.updateOp(selectedIndex, ArmTransferOP.of(getSelected().from(), new BlockPosFace(selectedPosFace.pos, selectedPosFace.facing), getSelected().filter()));
            }
        }).setPredicate(() -> getSelected() != null && selectedPosFace != null));

        // buttons
        addWidget(new ToggleButtonWidget(2, 100, 18, 18, GuiTextures.BLOCKS_INPUT, executor::isBlockMode, executor::setBlockMode)
            .setTooltipText("block mode"));

        addWidget(new ToggleButtonWidget(2 + 20, 100, 18, 18, GuiTextures.PROGRESS_BAR_MIXER, executor::isQueueMode, executor::setQueueMode)
            .setTooltipText("queue mode"));

        addWidget(new ToggleButtonWidget(2 + 40, 100, 18, 18, GuiTextures.BUTTON_BLACKLIST, executor::isRandomMode, executor::setRandomMode)
            .setTooltipText("random mode"));

        // add
        addWidget(new PredicatedButtonWidget(getSize().width - 20 * 4, 100, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, Icons.ADD), (cd) -> {
            if (!isRemote() && lastOps.size() < executor.getMaxOpCount()) {
                executor.addOp(ArmTransferOP.of(new BlockPosFace(executor.getMachine().getPos(), Direction.UP), new BlockPosFace(executor.getMachine().getPos(), Direction.DOWN)));
            }
        }).setPredicate(() -> lastOps.size() < executor.getMaxOpCount()));
        // remove
        addWidget(new PredicatedButtonWidget(getSize().width - 20 * 3, 100, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, Icons.REMOVE), (cd) -> {
            if (!isRemote()) {
                var selected = getSelected();
                if (selected != null) {
                    executor.removeOp(selectedIndex);
                }
            }
            selectedIndex = -1;
        }).setPredicate(() -> getSelected() != null));
        // move up
        addWidget(new PredicatedButtonWidget(getSize().width - 20 * 2, 100, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, Icons.UP), (cd) -> {
            if (!isRemote() && selectedIndex > 0) {
                var last = lastOps.get(selectedIndex - 1);
                var selected = getSelected();
                lastOps.set(selectedIndex, last);
                lastOps.set(selectedIndex - 1, selected);
                selectedIndex--;
                writeClientAction(0, buf -> buf.writeVarInt(selectedIndex));
            }
        }).setPredicate(() -> selectedIndex > 0 && selectedIndex < lastOps.size()));
        // move down
        addWidget(new PredicatedButtonWidget(getSize().width - 20, 100, 18, 18, new GuiTextureGroup(GuiTextures.BUTTON, Icons.DOWN), (cd) -> {
            if (!isRemote() && selectedIndex < lastOps.size() - 1) {
                var next = lastOps.get(selectedIndex + 1);
                var selected = getSelected();
                lastOps.set(selectedIndex, next);
                lastOps.set(selectedIndex + 1, selected);
                selectedIndex++;
                writeClientAction(0, buf -> buf.writeVarInt(selectedIndex));
            }
        }).setPredicate(() -> selectedIndex < lastOps.size() - 1 && selectedIndex >= 0));

        // list
        listWidget = new DraggableScrollableWidgetGroup(2, 120, getSize().width - 4, getSize().height - 120);
        listWidget.setBackground(new GuiTextureGroup(ColorPattern.BLACK.rectTexture(), ColorPattern.GRAY.borderTexture(1)));
        listWidget.setYScrollBarWidth(12);
        listWidget.setYBarStyle(GuiTextures.SLIDER_BACKGROUND_VERTICAL, GuiTextures.BUTTON);
        addWidget(listWidget);
    }

    private void initList() {
        listWidget.clearAllWidgets();
        int y = 0;
        int width = listWidget.getSize().width - 12;
        int height = 16;
        for (int i = 0; i < lastOps.size(); i++) {
            var wrapper = new SelectableWidgetGroup(0, y, width, height);
            var op = lastOps.get(i);
            // TODO move
            wrapper.addWidget(new ImageWidget(0, (height - 16) / 2, 16, 16, () -> new TextTexture("F")));
            wrapper.addWidget(new ImageWidget(18, (height - 16) / 2, 16, 16, () -> new ItemStackTexture(getOpItem(op.from().pos))));
            wrapper.addWidget(new ImageWidget(18 * 2, (height - 16) / 2, 16, 16, () -> new TextTexture("T")));
            wrapper.addWidget(new ImageWidget(18 * 3, (height - 16) / 2, 16, 16, () -> new ItemStackTexture(getOpItem(op.to().pos))));
            wrapper.setBackground(ColorPattern.T_BLACK.rectTexture());
            int finalI = i;
            wrapper.setOnSelected(s -> {
                s.setBackground(ColorPattern.T_WHITE.rectTexture());
                selectedIndex = finalI;
                selectedPosFace = null;
                writeClientAction(0, buf -> buf.writeVarInt(selectedIndex));
            });
            wrapper.setOnUnSelected(s -> s.setBackground(ColorPattern.T_BLACK.rectTexture()));
            listWidget.addWidget(wrapper);
            y += height;
        }
    }

    private ItemStack getOpItem(BlockPos pos) {
        if (pos == null || pos.equals(executor.getMachine().getPos())) {
            return ItemStack.EMPTY;
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
                        blocks.add(pos);
                    }
                }
            }
        }
        return blocks;
    }

    @OnlyIn(Dist.CLIENT)
    public void renderBlockOverLay(WorldSceneRenderer renderer) {
        sceneWidget.renderBlockOverLay(renderer);
        var selected = getSelected();
        if (selected != null) {
            var from = selected.from();
            var to = selected.to();
            if (!from.pos.equals(executor.getMachine().getPos())) {
                sceneWidget.drawFacingBorder(new PoseStack(), from, 0xffFF2111, 4);
            }
            if (!to.pos.equals(executor.getMachine().getPos())) {
                sceneWidget.drawFacingBorder(new PoseStack(), to, 0xff36FF1D, 4);
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
            initList();
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }
}