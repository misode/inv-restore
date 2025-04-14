package io.github.misode.invrestore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.misode.invrestore.InvRestoreEntityEquipment;
import io.github.misode.invrestore.InvRestoreInventory;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public record SnapshotItems(List<ItemStack> inventory, List<ItemStack> armor, List<ItemStack> offhand, List<ItemStack> enderChest) {
    public static final Codec<SnapshotItems> CODEC = RecordCodecBuilder.create(b -> b.group(
            itemListCodec(36).fieldOf("inventory").forGetter(SnapshotItems::inventory),
            itemListCodec(4).fieldOf("armor").forGetter(SnapshotItems::armor),
            itemListCodec(1).fieldOf("offhand").forGetter(SnapshotItems::offhand),
            itemListCodec(27).fieldOf("ender_chest").forGetter(SnapshotItems::enderChest)
    ).apply(b, SnapshotItems::new));

    public static SnapshotItems fromPlayer(ServerPlayer player) {
        Inventory inv = player.getInventory();
        InvRestoreEntityEquipment equipment = (InvRestoreEntityEquipment)((InvRestoreInventory)inv).inv_restore$getEquipment();
        PlayerEnderChestContainer end = player.getEnderChestInventory();
        return new SnapshotItems(
                ((InvRestoreInventory)inv).inv_restore$getItems().stream().map(ItemStack::copy).toList(),
                equipment.inv_restore$getArmor().stream().map(ItemStack::copy).toList(),
                equipment.inv_restore$getOffhand().stream().map(ItemStack::copy).toList(),
                end.items.stream().map(ItemStack::copy).toList()
        );
    }

    public int stackCount() {
        return this.allItems()
                .filter(item -> !item.isEmpty())
                .mapToInt(item -> 1)
                .sum();
    }

    public Stream<ItemStack> allItems() {
        return Stream.of(this.inventory, this.armor, this.offhand, this.enderChest)
                .flatMap(List::stream)
                .map(ItemStack::copy);
    }

    private static Codec<List<ItemStack>> itemListCodec(int size) {
        return Slot.CODEC.listOf().orElse(List.of()).xmap((slots) -> {
            List<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
            slots.forEach((slot) -> {
                items.set(slot.index, slot.item);
            });
            return items;
        }, (items) -> {
            List<Slot> slots = new ArrayList<>();
            for (int i = 0; i < items.size(); i += 1) {
                ItemStack item = items.get(i);
                if (!item.isEmpty()) {
                    slots.add(new Slot(i, item));
                }
            }
            return slots;
        });
    }

    record Slot(int index, ItemStack item) {
        public static final Codec<Slot> CODEC = RecordCodecBuilder.create(b -> b.group(
                Codec.intRange(0, 255).fieldOf("slot").forGetter(Slot::index),
                ItemStack.CODEC.fieldOf("item").forGetter(Slot::item)
        ).apply(b, Slot::new));
    }
}
