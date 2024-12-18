package io.github.misode.invrestore.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.gui.SlotGuiInterface;
import io.github.misode.invrestore.Styles;
import io.github.misode.invrestore.data.Snapshot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class SnapshotGui extends SimpleGui {
    private final Container container;

    public SnapshotGui(ServerPlayer player, Snapshot snapshot) {
        super(MenuType.GENERIC_9x5, player, false);
        List<ItemStack> items = snapshot.contents().allItems().toList();
        this.container = new TakeOnlyContainer(items);
        this.setTitle(Component.empty()
                .append(snapshot.event().formatEmoji(true))
                .append(" ")
                .append(Component.literal(snapshot.formatTimeAgo()).withStyle(Styles.GUI_DEFAULT))
                .append(" ")
                .append(Component.literal(snapshot.playerName()).withStyle(Styles.GUI_HIGHLIGHT))
        );
        initDefaultView();
    }

    private void mapSlot(int guiSlot, int containerSlot) {
        this.setSlotRedirect(guiSlot, new SnapshotSlot(this.container, containerSlot, 0, 0));
    }

    private void initDefaultView() {
        ItemStack switcher = Items.ENDER_CHEST.getDefaultInstance();
        switcher.set(DataComponents.ITEM_NAME, Component.literal("View Ender Chest").withStyle(Styles.LIST_HIGHLIGHT));
        for (int i = 0; i < this.size; i++) {
            if (i < 27) {
                this.mapSlot(i, i+9);
            } else if (i < 36) {
                this.mapSlot(i, i-27);
            } else if (i == 36) {
                this.setSlot(i, switcher, this::handleEnderChestClick);
            } else if (i == 40) {
                this.mapSlot(i, i);
            } else if (i >= 41) {
                this.mapSlot(i, i-5);
            } else {
                this.setSlot(i, ItemStack.EMPTY);
            }
        }
    }

    private void initEnderChestView() {
        ItemStack switcher = Items.CHEST.getDefaultInstance();
        switcher.set(DataComponents.ITEM_NAME, Component.literal("View Inventory").withStyle(Styles.LIST_HIGHLIGHT));
        for (int i = 0; i < this.size; i++) {
            if (i < 27) {
                this.mapSlot(i, i+41);
            } else if (i == 36) {
                this.setSlot(i, switcher, this::handleChestClick);
            } else {
                this.setSlot(i, ItemStack.EMPTY);
            }
        }
    }

    private void handleEnderChestClick(int index, ClickType type, Object action, SlotGuiInterface gui) {
        if (type.isLeft && !type.shift) {
            this.initEnderChestView();
        }
    }

    private void handleChestClick(int index, ClickType type, Object action, SlotGuiInterface gui) {
        if (type.isLeft && !type.shift) {
            this.initDefaultView();
        }
    }
}
