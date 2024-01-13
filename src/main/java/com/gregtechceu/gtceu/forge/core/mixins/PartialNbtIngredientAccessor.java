package com.gregtechceu.gtceu.forge.core.mixins;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.crafting.PartialNBTIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PartialNBTIngredient.class, remap = false)
public interface PartialNbtIngredientAccessor {
    @Accessor
    CompoundTag getNbt();
}
