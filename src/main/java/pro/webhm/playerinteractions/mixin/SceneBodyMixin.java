package pro.webhm.playerinteractions.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.webhm.playerinteractions.client.SceneCameraBridge;

@Mixin({LivingEntityRenderer.class})
public abstract class SceneBodyMixin {
   @Inject(
      method = {"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = {@At("HEAD")}
   )
   private void aotplayers$showSceneBody(
      LivingEntity entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci
   ) {
      if (entity == Minecraft.getInstance().getCameraEntity() && FirstPersonMode.isFirstPersonPass() && SceneCameraBridge.fullBody()) {
         if (((LivingEntityRenderer)(Object)this).getModel() instanceof PlayerModel<?> model) {
            model.body.visible = model.leftLeg.visible = model.rightLeg.visible = true;
            model.jacket.visible = model.leftPants.visible = model.rightPants.visible = true;
            model.head.visible = model.hat.visible = false;
         }
      }
   }
}
