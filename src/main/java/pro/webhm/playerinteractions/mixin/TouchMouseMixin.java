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
   // 1.20.1: MouseHandler.turnPlayer() takes no arguments (the double param was added in 1.20.2+/1.21)
   private void touch(CallbackInfo ci) {
      if (SceneCameraBridge.mouse.test(this.accumulatedDX, this.accumulatedDY)) {
         this.accumulatedDX = this.accumulatedDY = 0.0;
         ci.cancel();
      }
   }
}
