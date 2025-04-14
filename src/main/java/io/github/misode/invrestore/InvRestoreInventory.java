package io.github.misode.invrestore;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.item.ItemStack;

public interface InvRestoreInventory {
    NonNullList<ItemStack> inv_restore$getItems();
    EntityEquipment inv_restore$getEquipment();
}
