package dev.hollowatlas.mixin;

import dev.hollowatlas.world.ProtectionService;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entities (players and mobs) cannot trample the authored fields. */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
    @Inject(method = "setToDirt", at = @At("HEAD"), cancellable = true)
    private static void hollowatlas$noTrample(Entity entity, BlockState state, World world, BlockPos pos, CallbackInfo ci) {
        if (entity == null) return;
        if (entity instanceof PlayerEntity p && ProtectionService.isBuilder(p)) return;
        ci.cancel();
    }
}
