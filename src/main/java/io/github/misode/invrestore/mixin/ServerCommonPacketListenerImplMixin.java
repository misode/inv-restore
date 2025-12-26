package io.github.misode.invrestore.mixin;

import io.github.misode.invrestore.InvRestore;
import io.github.misode.invrestore.gui.SnapshotGui;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(method = "handleCustomClickAction", at = @At(value = "HEAD"), cancellable = true)
    private void handleCustomClickAction(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (!packet.id().equals(InvRestore.id("view_snapshot"))) {
            return;
        }
        ServerPlayer player = this.getServerPlayer();
        if (player == null) {
            return;
        }
        packet.payload()
                .flatMap(Tag::asCompound)
                .flatMap(c -> c.getString("id"))
                .flatMap(string -> InvRestore
                        .findSnapshots(s -> s.id().equals(string))
                        .stream().findAny())
                .ifPresent(snapshot -> {
                    try {
                        SnapshotGui gui = new SnapshotGui(player, snapshot);
                        gui.open();
                    } catch (Exception e) {
                        InvRestore.LOGGER.error("Failed to open GUI", e);
                    }
                });
        ci.cancel();
    }

    @Unique
    private ServerPlayer getServerPlayer() {
        if ((Object)this instanceof ServerGamePacketListenerImpl game) {
            return game.player;
        }
        return null;
    }
}
