package io.github.misode.invrestore.mixin;

import io.github.misode.invrestore.InvRestore;
import io.github.misode.invrestore.data.Snapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "die", at = @At("HEAD"), order = 100)
    private void die(DamageSource damageSource, CallbackInfo ci) {
        InvRestore.addSnapshot(Snapshot.fromDeath((ServerPlayer)(Object)this, damageSource));
    }
}
