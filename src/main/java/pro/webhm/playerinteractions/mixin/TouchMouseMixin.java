package pro.webhm.playerinteractions.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.webhm.playerinteractions.client.SceneCameraBridge;

@Mixin({MouseHandler.class})
public abstract class TouchMouseMixin {
   @Shadow
   private double accumulatedDX;
   @Shadow
   private double accumulatedDY;

   @Inject(
      method = {"turnPlayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void touch(double seconds, CallbackInfo ci) {
      if (SceneCameraBridge.mouse.test(this.accumulatedDX, this.accumulatedDY)) {
         this.accumulatedDX = this.accumulatedDY = 0.0;
         ci.cancel();
      }
   }
}
