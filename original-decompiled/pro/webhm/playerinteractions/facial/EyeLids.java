package pro.webhm.playerinteractions.facial;

public final class EyeLids {
   public static float upper(float closed, float slope, float curve, int side, float u) {
      float c = FacialData.clamp(closed, 0.0F, 1.0F);
      float arch = 1.0F - u * u;
      return FacialData.clamp(c + (1.0F - c) * ((float)(-side) * u * slope + curve * arch), 0.0F, 1.0F);
   }

   public static float lower(float closed, float lift, float u) {
      return 1.0F - FacialData.clamp(lift, 0.0F, 0.35F) * (1.0F - FacialData.clamp(closed, 0.0F, 1.0F)) * (1.0F - u * u);
   }

   private EyeLids() {
   }
}
