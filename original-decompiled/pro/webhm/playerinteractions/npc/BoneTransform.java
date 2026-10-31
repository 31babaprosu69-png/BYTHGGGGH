package pro.webhm.playerinteractions.npc;

public class BoneTransform {
   public float rx;
   public float ry;
   public float rz;
   public float height;
   public float shiftX;
   public float shiftZ;

   public BoneTransform copy() {
      BoneTransform t = new BoneTransform();
      t.rx = this.rx;
      t.ry = this.ry;
      t.rz = this.rz;
      t.height = this.height;
      t.shiftX = this.shiftX;
      t.shiftZ = this.shiftZ;
      return t;
   }

   public void set(BoneTransform src) {
      this.rx = src.rx;
      this.ry = src.ry;
      this.rz = src.rz;
      this.height = src.height;
      this.shiftX = src.shiftX;
      this.shiftZ = src.shiftZ;
   }

   public static BoneTransform lerp(BoneTransform a, BoneTransform b, float t) {
      BoneTransform r = new BoneTransform();
      r.rx = l(a.rx, b.rx, t);
      r.ry = l(a.ry, b.ry, t);
      r.rz = l(a.rz, b.rz, t);
      r.height = l(a.height, b.height, t);
      r.shiftX = l(a.shiftX, b.shiftX, t);
      r.shiftZ = l(a.shiftZ, b.shiftZ, t);
      return r;
   }

   private static float l(float a, float b, float t) {
      return a + (b - a) * t;
   }

   public void reset() {
      this.rx = this.ry = this.rz = 0.0F;
      this.height = 0.0F;
      this.shiftX = 0.0F;
      this.shiftZ = 0.0F;
   }
}
