package pro.webhm.playerinteractions.body;

public final class BlushMotion {
   private double last = Double.NaN;
   private float amount;

   public float sample(float target, double now, float rise, float fade) {
      if (!Double.isFinite(this.last) || now < this.last || now - this.last > 200.0) {
         this.last = now;
         this.amount = 0.0F;
      }

      double dt = Math.max(0.0, now - this.last);
      this.last = now;
      this.amount = this.amount
         + (target - this.amount) * (float)(1.0 - Math.exp(-dt / (double)(target > this.amount ? Math.max(40.0F, rise) : Math.max(1.0F, fade))));
      return this.amount * this.amount * (3.0F - 2.0F * this.amount);
   }
}
