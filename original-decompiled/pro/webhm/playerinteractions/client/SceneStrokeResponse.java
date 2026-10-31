package pro.webhm.playerinteractions.client;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class SceneStrokeResponse {
   public final Vector3f shift = new Vector3f();
   private final Vector3f wanted = new Vector3f();
   private final Vector3f tracking = new Vector3f();
   public float depth;
   public float contact;

   public void follow(float dt, Vector3fc right, Vector3fc up, float x, float y, float weight) {
      this.wanted.set(right).mul(x * 0.035F).fma(-y * 0.02F, up).mul(this.contact);
      if (this.wanted.lengthSquared() > 0.001225F) {
         this.wanted.normalize(0.035F);
      }

      this.tracking.lerp(this.wanted, 1.0F - (float)Math.exp((double)(-Math.max(0.0F, dt) * 0.24F)));
      this.shift.set(this.tracking).mul(weight);
      if (weight <= 0.0F) {
         this.shift.zero();
         this.tracking.zero();
         this.contact = 0.0F;
         this.depth = 0.0F;
      }
   }

   public float pressure(float dt, float requested, float gap, boolean clear) {
      float target = clear ? Math.max(0.0F, 1.0F - gap / 0.055F) : 0.0F;
      this.contact = this.contact + (target - this.contact) * (1.0F - (float)Math.exp((double)(-Math.max(0.0F, dt) * 0.65F)));
      this.depth = this.depth + (requested * target - this.depth) * (1.0F - (float)Math.exp((double)(-Math.max(0.0F, dt) * 0.65F)));
      return this.depth;
   }
}
