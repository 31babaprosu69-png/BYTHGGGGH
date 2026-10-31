package pro.webhm.playerinteractions.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.webhm.playerinteractions.InteractionServer;

@Mixin({Player.class})
public abstract class PlayerTravelMixin {
   @Inject(
      method = {"travel"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void aotplayers$travel(Vec3 movement, CallbackInfo ci) {
      Player player = (Player)(Object)this;
      if (!player.level().isClientSide && InteractionServer.locked(player.getUUID())) {
         player.setDeltaMovement(Vec3.ZERO);
         ci.cancel();
      }
   }
}
