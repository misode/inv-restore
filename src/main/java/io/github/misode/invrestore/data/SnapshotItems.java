package io.github.misode.invrestore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public record SnapshotItems(NonNullList<ItemStack> inventory, NonNullList<ItemStack> armor, NonNullList<ItemStack> offhand, NonNullList<ItemStack> enderChest) {
    public static final Codec<SnapshotItems> CODEC = RecordCodecBuilder.create(b -> b.group(
            itemListCodec(36).fieldOf("inventory").forGetter(SnapshotItems::inventory),
            itemListCodec(4).fieldOf("armor").forGetter(SnapshotItems::armor),
            itemListCodec(1).fieldOf("offhand").forGetter(SnapshotItems::offhand),
            itemListCodec(27).fieldOf("ender_chest").forGetter(SnapshotItems::enderChest)
    ).apply(b, SnapshotItems::new));

    public static SnapshotItems fromPlayer(ServerPlayer player) {
        Inventory inv = player.getInventory();
        PlayerEnderChestContainer end = player.getEnderChestInventory();
        return new SnapshotItems(inv.items, inv.armor, inv.offhand, end.items);
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

    private static Codec<NonNullList<ItemStack>> itemListCodec(int size) {
        return Slot.CODEC.listOf().orElse(List.of()).xmap((slots) -> {
            NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
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