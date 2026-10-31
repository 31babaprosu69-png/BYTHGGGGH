package pro.webhm.playerinteractions.adornments;

public final class AdornmentMotion {
   public float weight;
   public float tension;
   public float speed;
   public float swing;
   public float kneeRight;
   public float kneeLeft;
   public float lean;
   public float support;
   private double last = Double.NaN;
   private double distance;
   private float previousTension;

   public void update(double seconds, double travelled, float pull, boolean held) {
      if (Double.isNaN(this.last) || !(seconds <= this.last)) {
         float dt = Double.isNaN(this.last) ? 0.0F : (float)Math.min(0.1, seconds - this.last);
         this.last = seconds;
         if (!Double.isFinite(travelled) || travelled < 0.0 || travelled > 0.8) {
            travelled = 0.0;
         }

         this.weight = this.weight + ((float)(held ? 1 : 0) - this.weight) * (1.0F - (float)Math.exp((double)(-dt * (float)(held ? 8 : 5))));
         this.tension = this.tension + (Math.max(0.0F, Math.min(1.0F, pull)) - this.tension) * (1.0F - (float)Math.exp((double)(-dt * 7.0F)));
         float measured = dt > 0.0F ? (float)Math.min(1.0, travelled / (double)dt / 3.0) : 0.0F;
         this.speed = this.speed + (measured - this.speed) * (1.0F - (float)Math.exp((double)(-dt * 10.0F)));
         this.distance += travelled;
         double phase = this.distance * Math.PI * 2.0 / 1.65;
         this.swing = (float)Math.sin(phase) * 17.0F * this.speed * this.weight;
         this.kneeRight = (float)Math.max(0.0, -Math.sin(phase)) * 23.0F * this.speed * this.weight;
         this.kneeLeft = (float)Math.max(0.0, Math.sin(phase)) * 23.0F * this.speed * this.weight;
         float catchUp = Math.max(0.0F, this.tension - this.previousTension);
         this.previousTension = this.tension;
         this.lean = this.weight * (this.tension * 5.0F + Math.min(2.0F, catchUp * 35.0F));
         this.support = this.weight;
      }
   }
}
