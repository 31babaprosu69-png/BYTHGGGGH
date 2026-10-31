package pro.webhm.playerinteractions.adornments;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class AdornmentLead {
   public final Vector3f step = new Vector3f();
   public float yaw;
   private float speed;
   private int walking;
   private int settleTicks = 6;

   public AdornmentLead(float yaw) {
      this.yaw = yaw;
   }

   public void update(Vector3fc requested) {
      boolean settling = this.settleTicks > 0;
      if (settling) {
         this.settleTicks--;
      }

      float distance = (float)Math.hypot((double)requested.x(), (double)requested.z());
      if (distance < 1.0E-6F) {
         this.step.zero();
         this.speed = 0.0F;
      } else {
         float desired = (float)Math.toDegrees(Math.atan2((double)(-requested.x()), (double)requested.z()));
         float difference = (desired - this.yaw) % 360.0F;
         if (difference > 180.0F) {
            difference -= 360.0F;
         }

         if (difference < -180.0F) {
            difference += 360.0F;
         }

         this.yaw = this.yaw + Math.max(-12.0F, Math.min(12.0F, difference));
         if (settling) {
            this.speed = 0.0F;
            this.step.zero();
         } else {
            float facing = (float)Math.cos(Math.toRadians((double)(desired - this.yaw)));
            float wanted = distance * Math.max(0.0F, (facing - 0.25F) / 0.75F);
            this.speed = Math.min(wanted, this.speed + 0.025F);
            this.step.set(requested.x(), 0.0F, requested.z()).mul(this.speed / distance);
         }
      }
   }

   public boolean walking(boolean moved) {
      if (moved) {
         this.walking = 5;
      } else if (this.walking > 0) {
         this.walking--;
      }

      return this.walking > 0;
   }
}
