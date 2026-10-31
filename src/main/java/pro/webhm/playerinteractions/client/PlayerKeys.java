package pro.webhm.playerinteractions.client;

import net.minecraftforge.fml.common.Mod;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.AddLayers;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(
   modid = "aot_player_interactions",
   value = {Dist.CLIENT},
   bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class PlayerKeys {
   public static final KeyMapping OPEN = new KeyMapping("key.aot_players.interact", 86, "key.categories.aot_players");
   private static boolean waitOpenRelease;

   static boolean openDown() {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isWindowActive()) {
         return false;
      } else {
         Key key = OPEN.getKey();
         long window = mc.getWindow().getWindow();
         if (key.getValue() < 0) {
            return false;
         } else {
            return key.getType() == Type.MOUSE ? GLFW.glfwGetMouseButton(window, key.getValue()) == 1 : InputConstants.isKeyDown(window, key.getValue());
         }
      }
   }

   static void drainOpen() {
      waitOpenRelease = true;

      while (OPEN.consumeClick()) {
      }
   }

   static boolean consumeOpen() {
      if (!waitOpenRelease) {
         if (!OPEN.consumeClick()) {
            return false;
         } else {
            drainOpen();
            return true;
         }
      } else {
         while (OPEN.consumeClick()) {
         }

         if (!openDown()) {
            waitOpenRelease = false;
         }

         return false;
      }
   }

   @SubscribeEvent
   public static void keys(RegisterKeyMappingsEvent event) {
      event.register(OPEN);
   }

   @SubscribeEvent
   public static void setup(FMLClientSetupEvent event) {
      event.enqueueWork(PlayerAnimation::register);
   }

   @SubscribeEvent
   public static void layers(AddLayers event) {
      AvatarRenderer.register(event);
   }
}
