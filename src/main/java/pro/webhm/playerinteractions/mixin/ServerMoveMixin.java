package pro.webhm.playerinteractions.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.webhm.playerinteractions.InteractionServer;

@Mixin({ServerGamePacketListenerImpl.class})
public abstract class ServerMoveMixin {
   @Shadow
   public ServerPlayer player;

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void aotplayers$move(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
      if (this.player.server.isSameThread() && InteractionServer.locked(this.player.getUUID())) {
         if (InteractionServer.walkingLook(this.player.getUUID())) {
            float yaw = packet.getYRot(this.player.getYRot());
            float pitch = packet.getXRot(this.player.getXRot());
            if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
               this.player.setYRot(Mth.wrapDegrees(yaw));
               this.player.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
            }
         }

         ci.cancel();
      }
   }
}
