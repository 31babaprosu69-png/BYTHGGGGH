package pro.webhm.playerinteractions.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.webhm.playerinteractions.PairCollision;

@Mixin({Entity.class})
public abstract class PairPushMixin {
   @Inject(
      method = {"push(Lnet/minecraft/world/entity/Entity;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void aotplayers$pairPush(Entity other, CallbackInfo ci) {
      if (PairCollision.suppress((Entity)(Object)this, other)) {
         ci.cancel();
      }
   }
}
