package io.github.misode.invrestore.mixin;

import io.github.misode.invrestore.InvRestoreInventory;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Inventory.class)
public class InventoryMixin implements InvRestoreInventory {
    @Shadow
    @Final
    private NonNullList<ItemStack> items;

    @Shadow
    @Final
    private EntityEquipment equipment;

    @Override
    public NonNullList<ItemStack> inv_restore$getItems() {
        return this.items;
    }

    @Override
    public EntityEquipment inv_restore$getEquipment() {
        return this.equipment;
    }
}
