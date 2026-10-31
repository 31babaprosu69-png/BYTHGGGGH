package pro.webhm.playerinteractions;

import java.util.Map;
import net.minecraft.world.phys.Vec3;

public record TouchContact(Vec3 point, double distance, long start, float bodyYaw, float headYaw, float headPitch) {
   public boolean inRange(Vec3 eye, long now) {
      return now - this.start < 2400L && eye.distanceToSqr(this.point) <= 9.0;
   }

   public Vec3 force(Vec3 eye, Vec3 look, int zone, long now) {
      if (!this.inRange(eye, now)) {
         return null;
      } else {
         Vec3 delta = eye.add(look.scale(this.distance)).subtract(this.point);
         if (delta.lengthSqr() > 1.44) {
            return null;
         } else {
            Vec3 local = TouchZones.localForce(delta, zone, this.bodyYaw, this.headYaw, this.headPitch, Map.of());
            double length = local.length();
            return length > 1.5 ? local.scale(1.5 / length) : local;
         }
      }
   }
}
