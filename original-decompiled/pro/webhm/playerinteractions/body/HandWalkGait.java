package pro.webhm.playerinteractions.body;

public final class HandWalkGait {
   public static float swing(double distance, float speed) {
      return Double.isFinite(distance) && Float.isFinite(speed)
         ? (float)Math.sin(distance * 2.0 * Math.PI / 1.45) * 14.0F * Math.max(0.0F, Math.min(1.0F, speed))
         : 0.0F;
   }

   private HandWalkGait() {
   }
}
