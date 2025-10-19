package net.minecraft.enchantment;

import java.util.function.Consumer;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record EnchantmentEffectContext(ItemStack stack, @Nullable EquipmentSlot slot, @Nullable LivingEntity owner, Consumer<Item> onBreak) {
	public EnchantmentEffectContext(ItemStack stack, EquipmentSlot slot, LivingEntity owner) {
		this(stack, slot, owner, item -> owner.sendEquipmentBreakStatus(item, slot));
	}
}
