package pro.webhm.playerinteractions.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.webhm.playerinteractions.client.AppearanceClient;

@Mixin({AbstractClientPlayer.class})
public abstract class PlayerSkinMixin {
   // 1.20.1: the arm model is chosen via getModelName() ("default" = wide, "slim")
   @Inject(method = {"getModelName"}, at = {@At("RETURN")}, cancellable = true)
   private void aotplayers$arms(CallbackInfoReturnable<String> ci) {
      int choice = AppearanceClient.arms(((AbstractClientPlayer)(Object)this).getUUID());
      if (choice != 0) {
         ci.setReturnValue(choice == 2 ? "slim" : "default");
      }
   }
}
