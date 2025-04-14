package io.github.misode.invrestore;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface InvRestoreEntityEquipment {
    List<ItemStack> inv_restore$getArmor();
    List<ItemStack> inv_restore$getOffhand();
}
