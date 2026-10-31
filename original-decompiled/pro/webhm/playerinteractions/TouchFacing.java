package pro.webhm.playerinteractions;

import net.minecraft.util.Mth;

public final class TouchFacing {
   public static float relative(float head, float body) {
      return Mth.clamp(Mth.wrapDegrees(head - body), -65.0F, 65.0F);
   }

   private TouchFacing() {
   }
}
