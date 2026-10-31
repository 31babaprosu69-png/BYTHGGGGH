package pro.webhm.playerinteractions.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/** 1.20.1 replacement for the 1.20.2+ PlayerSkin record (model + texture). */
public final class SkinCompat {
   public static boolean slim(AbstractClientPlayer p) {
      return "slim".equals(p.getModelName());
   }

   public static ResourceLocation texture(AbstractClientPlayer p) {
      return p.getSkinTextureLocation();
   }

   private SkinCompat() {
   }
}
