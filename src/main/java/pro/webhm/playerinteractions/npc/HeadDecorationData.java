package pro.webhm.playerinteractions.npc;

public class HeadDecorationData {
   public float x;
   public float y = -9.0F;
   public float z;
   public float width = 2.0F;
   public float height = 3.0F;
   public float depth = 2.0F;
   public float pitch;
   public float yaw;
   public float roll;
   public int u = 8;
   public int v = 8;
   public boolean locked;
   public boolean solidColor;
   public int rgb = 16777215;
   public int shape;

   public void normalize() {
      this.rgb &= 16777215;
      this.shape = Math.max(0, Math.min(2, this.shape));
      this.x = safe(this.x, -32.0F, 32.0F);
      this.y = safe(this.y, -32.0F, 32.0F);
      this.z = safe(this.z, -32.0F, 32.0F);
      this.width = safe(this.width, 0.1F, 16.0F);
      this.height = safe(this.height, 0.1F, 16.0F);
      this.depth = safe(this.depth, 0.1F, 16.0F);
      this.pitch = safe(this.pitch, -180.0F, 180.0F);
      this.yaw = safe(this.yaw, -180.0F, 180.0F);
      this.roll = safe(this.roll, -180.0F, 180.0F);
      this.u = Math.max(0, Math.min(63, this.u));
      this.v = Math.max(0, Math.min(63, this.v));
   }

   private static float safe(float n, float min, float max) {
      return Float.isFinite(n) ? Math.max(min, Math.min(max, n)) : min;
   }
}
