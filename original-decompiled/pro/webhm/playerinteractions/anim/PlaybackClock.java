package pro.webhm.playerinteractions.anim;

public final class PlaybackClock {
   private double value;
   private double at;
   private double target;
   private double targetAt;
   private boolean initialized;
   private boolean paused;

   public void update(double elapsed, long serverTick, boolean pause, double now) {
      double wanted = elapsed + (pause ? 0.0 : Math.max(0.0, now - (double)serverTick));
      if (!this.initialized) {
         this.initialized = true;
         this.value = wanted;
         this.at = now;
      } else {
         this.sample(now);
      }

      if (pause) {
         this.value = elapsed;
         this.at = Math.max(this.at, now);
      }

      this.target = wanted;
      this.targetAt = now;
      this.paused = pause;
   }

   public double sample(double now) {
      if (now <= this.at) {
         return this.value;
      } else {
         double dt = now - this.at;
         if (!this.paused) {
            double error = this.target + now - this.targetAt - (this.value + dt);
            this.value = this.value + dt + Math.max(-dt * 0.1, Math.min(dt * 0.1, error));
         }

         this.at = now;
         return this.value;
      }
   }
}
