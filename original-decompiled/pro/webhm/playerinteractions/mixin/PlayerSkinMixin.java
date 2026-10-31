package pro.webhm.playerinteractions.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.PlayerSkin.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.webhm.playerinteractions.client.AppearanceClient;

@Mixin({AbstractClientPlayer.class})
public abstract class PlayerSkinMixin {
   @Inject(
      method = {"getSkin"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void aotplayers$arms(CallbackInfoReturnable<PlayerSkin> ci) {
      int choice = AppearanceClient.arms(((AbstractClientPlayer)this).getUUID());
      if (choice != 0) {
         PlayerSkin s = (PlayerSkin)ci.getReturnValue();
         Model model = choice == 2 ? Model.SLIM : Model.WIDE;
         if (s.model() != model) {
            ci.setReturnValue(new PlayerSkin(s.texture(), s.textureUrl(), s.capeTexture(), s.elytraTexture(), model, s.secure()));
         }
      }
   }
}
