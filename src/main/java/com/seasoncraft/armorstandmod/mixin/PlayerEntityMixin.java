package com.seasoncraft.armorstandmod.mixin;

import com.seasoncraft.armorstandmod.ArmorStandMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity {
    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public void onEquipStack(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack) {
        super.onEquipStack(slot, oldStack, newStack);
        if (slot == EquipmentSlot.CHEST) {
            ArmorStandMod.updateArmorStandHeadForPlayer((PlayerEntity)(Object)this, newStack);
        }
    }
}
