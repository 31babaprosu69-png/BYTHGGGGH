package pro.webhm.playerinteractions.anim;

public final class StaminaVisual {
   private int target = -1;
   private double from;
   private double changedAt;

   public void update(int count, double now) {
      count = Math.max(0, Math.min(12, count));
      if (this.target < 0) {
         this.from = (double)count;
         this.target = count;
         this.changedAt = now;
      } else if (this.target != count) {
         this.from = (double)this.sample(now);
         this.target = count;
         this.changedAt = now;
      }
   }

   public float sample(double now) {
      double t = Math.max(0.0, Math.min(1.0, (now - this.changedAt) / 7.0));
      return (float)(this.from + ((double)this.target - this.from) * t);
   }
}
