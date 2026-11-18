package dev.hollowatlas.mixin;

import dev.hollowatlas.inventory.ModItems;
import dev.hollowatlas.world.ProtectionService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Key items can't be thrown away: a drop attempt returns the item to the inventory. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void hollowatlas$keepKeyItems(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient || stack.isEmpty() || !ModItems.isKeyItem(stack) || ProtectionService.isBuilder(self)) return;
        if (!self.isAlive()) return; // death with keepInventory never drops anyway
        ItemStack copy = stack.copy();
        if (!self.getInventory().insertStack(copy)) self.getInventory().offerOrDrop(copy);
        self.sendMessage(Text.translatable("message.hollowatlas.cannot_drop_key").formatted(Formatting.GOLD), true);
        self.currentScreenHandler.syncState();
        cir.setReturnValue(null);
    }
}
