package io.github.misode.invrestore.mixin;

import io.github.misode.invrestore.InvRestoreEntityEquipment;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.EnumMap;
import java.util.List;

@Mixin(EntityEquipment.class)
public class EntityEquipmentMixin implements InvRestoreEntityEquipment {
    @Shadow
    @Final
    private EnumMap<EquipmentSlot, ItemStack> items;

    @Override
    public List<ItemStack> inv_restore$getArmor() {
        return List.of(
                items.getOrDefault(EquipmentSlot.FEET, ItemStack.EMPTY),
                items.getOrDefault(EquipmentSlot.LEGS, ItemStack.EMPTY),
                items.getOrDefault(EquipmentSlot.CHEST, ItemStack.EMPTY),
                items.getOrDefault(EquipmentSlot.HEAD, ItemStack.EMPTY)
        );
    }

    @Override
    public List<ItemStack> inv_restore$getOffhand() {
        return List.of(items.getOrDefault(EquipmentSlot.OFFHAND, ItemStack.EMPTY));
    }
}
