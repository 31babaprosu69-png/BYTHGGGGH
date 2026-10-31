package pro.webhm.playerinteractions.facial;

public record FacialTongue(float extension, float side, float curl) {
   public static final FacialTongue HIDDEN = new FacialTongue(0.0F, 0.0F, 0.0F);

   public FacialTongue(float extension, float side, float curl) {
      extension = finite(extension, 0.0F, 1.0F);
      side = finite(side, -0.25F, 0.25F);
      curl = finite(curl, -0.18F, 0.22F);
      this.extension = extension;
      this.side = side;
      this.curl = curl;
   }

   private static float finite(float x, float min, float max) {
      return Float.isFinite(x) ? FacialData.clamp(x, min, max) : 0.0F;
   }

   public static FacialTongue sample(double age, float contact, float dragX, float dragY, float finger) {
      return FacePerformance.tongue(age, contact, dragX, dragY, finger, 0);
   }
}
