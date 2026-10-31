package pro.webhm.playerinteractions.body;

public final class TouchSpring {
   public float x;
   public float y;
   public float z;
   private float vx;
   private float vy;
   private float vz;
   private double previous = Double.NaN;

   public void sample(double ticks, float tx, float ty, float tz) {
      if (Double.isFinite(ticks)) {
         if (Double.isNaN(this.previous)) {
            this.previous = ticks;
         } else {
            double dt = (ticks - this.previous) / 20.0;
            this.previous = ticks;
            if (!(dt < 0.0) && !(dt > 0.5)) {
               float length = (float)Math.sqrt((double)(tx * tx + ty * ty + tz * tz));
               if (!Float.isFinite(length)) {
                  tz = 0.0F;
                  ty = 0.0F;
                  tx = 0.0F;
               } else if (length > 1.5F) {
                  tx *= 1.5F / length;
                  ty *= 1.5F / length;
                  tz *= 1.5F / length;
               }

               while (dt > 0.0) {
                  float h = (float)Math.min(dt, 0.008333333333333333);
                  this.vx = this.vx + (110.0F * (tx - this.x) - 15.0F * this.vx) * h;
                  this.vy = this.vy + (110.0F * (ty - this.y) - 15.0F * this.vy) * h;
                  this.vz = this.vz + (110.0F * (tz - this.z) - 15.0F * this.vz) * h;
                  this.x = this.x + this.vx * h;
                  this.y = this.y + this.vy * h;
                  this.z = this.z + this.vz * h;
                  dt -= (double)h;
               }

               this.x = Math.max(-1.7F, Math.min(1.7F, this.x));
               this.y = Math.max(-1.7F, Math.min(1.7F, this.y));
               this.z = Math.max(-1.7F, Math.min(1.7F, this.z));
            } else {
               this.x = this.y = this.z = this.vx = this.vy = this.vz = 0.0F;
            }
         }
      }
   }
}
