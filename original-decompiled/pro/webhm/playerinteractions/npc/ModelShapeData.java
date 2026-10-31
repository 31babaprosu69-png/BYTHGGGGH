package pro.webhm.playerinteractions.npc;

public class ModelShapeData {
   public float armDepth = 4.0F;
   public float armHeight = 1.0F;
   public float armPivotHeight = 0.0F;
   public float torsoWidth = 8.0F;
   public float waistWidth = 8.0F;
   public float hipWidth = 8.0F;
   public boolean shapeSecondLayer = false;
   public float headWidth = 1.0F;
   public float headHeight = 1.0F;
   public float headDepth = 1.0F;

   public void normalize() {
      this.armDepth = clamp(this.armDepth, 2.0F, 6.0F);
      this.armHeight = clamp(this.armHeight, 0.5F, 1.5F);
      this.armPivotHeight = clamp(this.armPivotHeight, -4.0F, 4.0F);
      this.torsoWidth = clamp(this.torsoWidth, 4.0F, 12.0F);
      this.waistWidth = clamp(this.waistWidth, 3.0F, 10.0F);
      this.hipWidth = clamp(this.hipWidth, 4.0F, 12.0F);
      this.headWidth = clamp(this.headWidth, 0.5F, 1.75F);
      this.headHeight = clamp(this.headHeight, 0.5F, 1.75F);
      this.headDepth = clamp(this.headDepth, 0.5F, 1.75F);
   }

   public void reset() {
      this.armDepth = 4.0F;
      this.armHeight = 1.0F;
      this.armPivotHeight = 0.0F;
      this.torsoWidth = this.waistWidth = this.hipWidth = 8.0F;
      this.headWidth = this.headHeight = this.headDepth = 1.0F;
   }

   public boolean hasCustomTorso() {
      return Math.abs(this.torsoWidth - 8.0F) > 0.001F || Math.abs(this.waistWidth - 8.0F) > 0.001F || Math.abs(this.hipWidth - 8.0F) > 0.001F;
   }

   private static float clamp(float value, float min, float max) {
      return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : 1.0F;
   }
}
