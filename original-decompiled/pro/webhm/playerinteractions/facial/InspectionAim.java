package pro.webhm.playerinteractions.facial;

public final class InspectionAim {
   private double previous = Double.NaN;
   private double x;
   private double y;
   private double vx;
   private double vy;

   public static float yawDelta(float yaw, float origin) {
      if (Float.isFinite(yaw) && Float.isFinite(origin)) {
         float angle = (yaw - origin) % 360.0F;
         return angle >= 180.0F ? angle - 360.0F : (angle < -180.0F ? angle + 360.0F : angle);
      } else {
         return 0.0F;
      }
   }

   public void sample(double tick, float yaw, float pitch) {
      if (Double.isFinite(tick) && (Double.isNaN(this.previous) || !(tick <= this.previous))) {
         double dt = Double.isNaN(this.previous) ? 0.0 : Math.min(0.25, (tick - this.previous) / 20.0);
         this.previous = tick;
         double tx = (double)FacialData.clamp(yaw / 35.0F, -1.0F, 1.0F);
         double ty = (double)FacialData.clamp(pitch / 28.0F, -1.0F, 1.0F);
         double decay = Math.exp(-14.0 * dt);
         double dx = this.x - tx;
         double dy = this.y - ty;
         double cx = this.vx + 14.0 * dx;
         double cy = this.vy + 14.0 * dy;
         this.x = tx + (dx + cx * dt) * decay;
         this.y = ty + (dy + cy * dt) * decay;
         this.vx = (this.vx - 14.0 * cx * dt) * decay;
         this.vy = (this.vy - 14.0 * cy * dt) * decay;
      }
   }

   public float x() {
      return (float)this.x;
   }

   public float y() {
      return (float)this.y;
   }

   public FacialActing pose() {
      return pose(this.x(), this.y());
   }

   public static FacialActing pose(float x, float y) {
      x = FacialData.clamp(x, -1.0F, 1.0F);
      y = FacialData.clamp(y, -1.0F, 1.0F);
      return x == 0.0F && y == 0.0F
         ? FacialActing.NONE
         : new FacialActing(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -30.0F * x, 22.0F * y, 9.0F * x * (1.0F - 0.25F * Math.abs(y)), 0.0F, 0.0F, 0.0F);
   }
}
