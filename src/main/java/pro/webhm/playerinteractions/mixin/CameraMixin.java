package pro.webhm.playerinteractions.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.webhm.playerinteractions.client.SceneCameraBridge;

@Mixin({Camera.class})
public abstract class CameraMixin {
   @Shadow
   protected abstract void setPosition(Vec3 var1);

   @Inject(
      method = {"setup"},
      at = {@At("TAIL")}
   )
   private void aotplayers$placeSceneCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
      Vec3 position = SceneCameraBridge.position((Camera)this, partialTick);
      if (position != null) {
         this.setPosition(position);
      }
   }
}
