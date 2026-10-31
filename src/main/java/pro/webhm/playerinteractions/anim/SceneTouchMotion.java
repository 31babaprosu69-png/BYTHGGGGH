package pro.webhm.playerinteractions.anim;

public class SceneTouchMotion {
   public float x;
   public float y;
   public float weight;
   public float pressureAmount;
   public float pressureFade = 1.0F;
   private double sample = Double.NaN;
   private float releaseWeight = -1.0F;

   public boolean advance(long tick, float partial, float tx, float ty, boolean pressed, long release) {
      return this.advance((double)tick + (double)partial, tx, ty, pressed, release);
   }

   public boolean advance(double now, float tx, float ty, boolean pressed, long release) {
      if (Double.isFinite(now) && Float.isFinite(tx) && Float.isFinite(ty)) {
         if (Double.isNaN(this.sample)) {
            this.sample = now;
         }

         if (now <= this.sample) {
            return false;
         } else {
            float dt = (float)Math.min(2.0, now - this.sample);
            this.sample = now;
            float a = 1.0F - (float)Math.exp((double)(-dt) * 0.65);
            this.x = this.x + (tx - this.x) * a;
            this.y = this.y + (ty - this.y) * a;
            this.pressureAmount = this.pressureAmount + ((float)(pressed && release < 0L ? 1 : 0) - this.pressureAmount) * a;
            if (release >= 0L) {
               if (this.releaseWeight < 0.0F) {
                  this.releaseWeight = this.weight;
               }

               float t = (float)((now - (double)release) / 6.0);
               this.pressureFade = 1.0F - clamp(t * 2.0F);
               this.weight = this.releaseWeight * (1.0F - clamp((t - 0.5F) * 2.0F));
            } else {
               this.weight = Math.min(1.0F, this.weight + dt / 6.0F);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private static float clamp(float n) {
      return Math.max(0.0F, Math.min(1.0F, n));
   }
}
