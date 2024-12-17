package io.github.misode.invrestore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.stream.Stream;

public record ItemContents(ItemContainerContents inventory, ItemContainerContents armor, ItemContainerContents offhand, ItemContainerContents enderChest) {
    public static final Codec<ItemContents> CODEC = RecordCodecBuilder.create(b -> b.group(
            ItemContainerContents.CODEC.fieldOf("inventory").orElse(ItemContainerContents.EMPTY).forGetter(ItemContents::inventory),
            ItemContainerContents.CODEC.fieldOf("armor").orElse(ItemContainerContents.EMPTY).forGetter(ItemContents::armor),
            ItemContainerContents.CODEC.fieldOf("offhand").orElse(ItemContainerContents.EMPTY).forGetter(ItemContents::offhand),
            ItemContainerContents.CODEC.fieldOf("ender_chest").orElse(ItemContainerContents.EMPTY).forGetter(ItemContents::enderChest)
    ).apply(b, ItemContents::new));

    public static ItemContents fromPlayer(ServerPlayer player) {
        Inventory inv = player.getInventory();
        PlayerEnderChestContainer end = player.getEnderChestInventory();
        return new ItemContents(
                ItemContainerContents.fromItems(inv.items),
                ItemContainerContents.fromItems(inv.armor),
                ItemContainerContents.fromItems(inv.offhand),
                ItemContainerContents.fromItems(end.items)
        );
    }

    public int stackCount() {
        return this.allItems()
                .mapToInt(item -> 1)
                .sum();
    }

    public Stream<ItemStack> allItems() {
        return Stream.of(this.inventory, this.armor, this.offhand, this.enderChest)
                .flatMap(ItemContainerContents::stream)
                .filter(item -> !item.isEmpty());
    }
}
