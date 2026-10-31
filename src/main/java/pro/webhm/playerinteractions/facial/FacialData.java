package pro.webhm.playerinteractions.facial;

public final class FacialData {
   public boolean enabled;
   public boolean autoExpressions = true;
   public boolean speechMouth = true;
   public float x;
   public float y = 6.2F;
   public float width = 2.2F;
   public float height = 1.3F;
   public float jawDrop = 1.1F;
   public float upperLip = 0.1F;
   public float lowerLip = 0.16F;
   public float lipProjection = 0.1F;
   public int lipColor = -3703671;
   public float open;
   public float smile;
   public float brow;
   public float squint;
   public float lettersPerSecond = 12.0F;
   public float depth = 1.1F;
   public float lipVolume = 0.12F;
   public float cheekLift = 0.18F;
   public float expressionLink = 0.65F;
   public float emotionStrength = 0.85F;
   public float headMotion = 0.65F;
   public boolean speechGestures = true;
   public float gestureStrength = 0.65F;
   public boolean teeth = true;
   public boolean tongue = true;
   public int innerColor = -12905437;
   public int teethColor = -5155;
   public int tongueColor = -4694411;

   public void normalize() {
      this.width = clamp(this.width, 0.5F, 4.0F);
      this.x = clamp(this.x, -3.0F + this.width / 2.0F, 3.0F - this.width / 2.0F);
      this.y = clamp(this.y, 5.0F, 7.9F);
      this.height = clamp(this.height, 0.3F, 2.0F);
      this.jawDrop = clamp(this.jawDrop, 0.0F, 1.8F);
      this.open = clamp(this.open, 0.0F, 1.0F);
      this.smile = clamp(this.smile, -1.0F, 1.0F);
      this.brow = clamp(this.brow, -1.0F, 1.0F);
      this.squint = clamp(this.squint, 0.0F, 1.0F);
      this.lettersPerSecond = clamp(this.lettersPerSecond, 4.0F, 30.0F);
      this.depth = clamp(this.depth, 0.3F, 1.8F);
      this.lipVolume = clamp(this.lipVolume, 0.0F, 0.3F);
      this.cheekLift = clamp(this.cheekLift, 0.0F, 0.35F);
      this.expressionLink = clamp(this.expressionLink, 0.0F, 1.0F);
      this.emotionStrength = clamp(this.emotionStrength, 0.0F, 1.0F);
      this.headMotion = clamp(this.headMotion, 0.0F, 1.0F);
      this.gestureStrength = clamp(this.gestureStrength, 0.0F, 1.0F);
      this.upperLip = clamp(this.upperLip, 0.0F, 0.35F);
      this.lowerLip = clamp(this.lowerLip, 0.0F, 0.45F);
      this.lipProjection = clamp(this.lipProjection, 0.0F, 0.3F);
      this.lipColor |= -16777216;
      this.innerColor |= -16777216;
      this.teethColor |= -16777216;
      this.tongueColor |= -16777216;
   }

   public static float clamp(float x, float lo, float hi) {
      return Float.isFinite(x) ? Math.max(lo, Math.min(hi, x)) : lo;
   }
}
