package pro.webhm.playerinteractions.facial;

public record FacialActing(
   float gazeX,
   float gazeY,
   float gazeWeight,
   float blinkLeft,
   float blinkRight,
   float headYaw,
   float headPitch,
   float headRoll,
   float blush,
   float asymmetry,
   float eyeWide
) {
   public static final FacialActing NONE = new FacialActing(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

   public FacialActing blend(FacialActing b, float t) {
      return new FacialActing(
         m(this.gazeX, b.gazeX, t),
         m(this.gazeY, b.gazeY, t),
         m(this.gazeWeight, b.gazeWeight, t),
         m(this.blinkLeft, b.blinkLeft, t),
         m(this.blinkRight, b.blinkRight, t),
         m(this.headYaw, b.headYaw, t),
         m(this.headPitch, b.headPitch, t),
         m(this.headRoll, b.headRoll, t),
         m(this.blush, b.blush, t),
         m(this.asymmetry, b.asymmetry, t),
         m(this.eyeWide, b.eyeWide, t)
      );
   }

   private static float m(float a, float b, float t) {
      return a + (b - a) * t;
   }
}
