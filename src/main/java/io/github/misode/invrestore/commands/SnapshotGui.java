package io.github.misode.invrestore.commands;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.gui.SlotGuiInterface;
import io.github.misode.invrestore.Styles;
import io.github.misode.invrestore.data.ItemContents;
import io.github.misode.invrestore.data.Snapshot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SnapshotGui extends SimpleGui {
    private final Snapshot snapshot;

    public SnapshotGui(ServerPlayer player, Snapshot snapshot) {
        super(MenuType.GENERIC_9x5, player, true);
        this.snapshot = snapshot;
        this.setLockPlayerInventory(true);
        this.setTitle(Component.empty()
                .append(snapshot.event().formatEmoji(true))
                .append(" ")
                .append(Component.literal(snapshot.formatTimeAgo()).withStyle(Styles.GUI_DEFAULT))
                .append(" ")
                .append(Component.literal(snapshot.playerName()).withStyle(Styles.GUI_HIGHLIGHT))
        );
        initDefaultView();
    }

    private void initDefaultView() {
        ItemContents contents = this.snapshot.contents();
        ItemStack switcher = Items.ENDER_CHEST.getDefaultInstance();
        switcher.set(DataComponents.ITEM_NAME, Component.literal("View Ender Chest").withStyle(Styles.LIST_HIGHLIGHT));
        for (int i = 0; i < this.size; i++) {
            if (i < 27) {
                this.setSlot(i, contents.inventoryGet(i + 9));
            } else if (i < 36) {
                this.setSlot(i, contents.inventoryGet(i-27));
            } else if (i == 36) {
                this.setSlot(i, switcher, this::handleEnderChestClick);
            } else if (i == 40) {
                this.setSlot(i, contents.offhandGet(0));
            } else if (i >= 41) {
                this.setSlot(i, contents.armorGet(i-41));
            } else {
                this.setSlot(i, ItemStack.EMPTY);
            }
        }
    }

    private void initEnderChestView() {
        ItemContents contents = this.snapshot.contents();
        ItemStack switcher = Items.CHEST.getDefaultInstance();
        switcher.set(DataComponents.ITEM_NAME, Component.literal("View Inventory").withStyle(Styles.LIST_HIGHLIGHT));
        for (int i = 0; i < this.size; i++) {
            if (i < 27) {
                this.setSlot(i, contents.enderChestGet(i));
            } else if (i == 36) {
                this.setSlot(i, switcher, this::handleChestClick);
            } else {
                this.setSlot(i, ItemStack.EMPTY);
            }
        }
    }

    private void handleEnderChestClick(int index, ClickType type, Object action, SlotGuiInterface gui) {
        if (type.isLeft) {
            this.initEnderChestView();
        }
    }

    private void handleChestClick(int index, ClickType type, Object action, SlotGuiInterface gui) {
        if (type.isLeft) {
            this.initDefaultView();
        }
    }
}
