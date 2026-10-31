package pro.webhm.playerinteractions.adornments;

import org.joml.Vector3f;

public final class AdornmentReachFeedback {
   private double sampled = Double.NaN;
   private double last = Double.NaN;
   private final Vector3f target = new Vector3f();
   private final Vector3f filtered = new Vector3f();

   public void sample(double now, double x, double y, double z) {
      if (Double.isFinite(now) && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
         this.sampled = now;
         this.target.set((float)x, 0.0F, (float)z);
         double size = Math.hypot(x, z);
         if (size > 1.0) {
            this.target.set((float)(x / size), 0.0F, (float)(z / size));
         }

         if (size < 0.015) {
            this.target.zero();
         }
      }
   }

   public Vector3f step(double now, Vector3f out) {
      if (!Double.isFinite(now)) {
         return out.zero();
      } else {
         double dt = Double.isNaN(this.last) ? 0.05 : Math.max(0.0, Math.min(0.1, now - this.last));
         if (!Double.isNaN(this.last) && now < this.last) {
            this.filtered.zero();
            this.sampled = Double.NaN;
         }

         this.last = now;
         boolean fresh = !Double.isNaN(this.sampled) && now >= this.sampled && now - this.sampled <= 0.4;
         if (!fresh) {
            this.target.zero();
         }

         this.filtered.lerp(this.target, (float)(1.0 - Math.exp(-dt * 12.0)));
         out.set(this.filtered).mul(0.55F);
         float length = out.length();
         if (length > 0.12F) {
            out.mul(0.12F / length);
         }

         return out;
      }
   }
}
