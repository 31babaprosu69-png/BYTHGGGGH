package pro.webhm.playerinteractions.anim;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SceneGuest {
   public int joinTick;
   public String appearance = "";
   public String clip = "";
   public ActorMotion origin = new ActorMotion();
   public ActorMotion point = new ActorMotion();
   public ActorMotion spawnPoint;
   private transient String nameSource;
   private transient String nameCache;

   public boolean valid() {
      return this.joinTick >= 0
         && this.appearance != null
         && !this.appearance.isBlank()
         && this.clip != null
         && !this.clip.isBlank()
         && this.origin != null
         && this.point != null
         && finite(this.origin)
         && finite(this.point)
         && (this.spawnPoint == null || finite(this.spawnPoint));
   }

   private static boolean finite(ActorMotion p) {
      return Float.isFinite(p.x)
         && Float.isFinite(p.y)
         && Float.isFinite(p.z)
         && Float.isFinite(p.yaw)
         && Math.abs(p.x) <= 64.0F
         && Math.abs(p.y) <= 32.0F
         && Math.abs(p.z) <= 64.0F;
   }

   public boolean recoverSpawnPoint(int hostEntryTicks) {
      if (this.spawnPoint == null) {
         NpcAnimation animation = NpcAnimation.fromJson(this.clip);
         if (animation.isEmpty()) {
            return false;
         }

         ActorMotion saved = animation.sampleMotion((double)Math.max(0, this.joinTick - Math.max(0, hostEntryTicks)), false);
         if (saved == null || !finite(saved)) {
            return false;
         }

         this.spawnPoint = saved.copy();
      }

      return finite(this.spawnPoint);
   }

   public static boolean atApproach(double x, double y, double z, double[] target) {
      double dx = x - target[0];
      double dz = z - target[2];
      return dx * dx + dz * dz < 0.64 && Math.abs(y - target[1]) < 1.1;
   }

   public void rebasePoints(double ox, double oy, double oz, float yaw, double nx, double ny, double nz, float nextYaw) {
      this.point = this.point.rebase(ox, oy, oz, yaw, nx, ny, nz, nextYaw);
      if (this.spawnPoint != null) {
         this.spawnPoint = this.spawnPoint.rebase(ox, oy, oz, yaw, nx, ny, nz, nextYaw);
      }
   }

   public static double[] world(ActorMotion local, double x, double y, double z, float yaw) {
      double angle = Math.toRadians((double)yaw);
      double sin = Math.sin(angle);
      double cos = Math.cos(angle);
      return new double[]{
         x + (double)local.x * cos - (double)local.z * sin, y + (double)local.y, z + (double)local.z * cos + (double)local.x * sin, (double)(yaw + local.yaw)
      };
   }

   public static boolean behindCamera(AABB box, Vec3 camera, Vec3 view) {
      for (double x : new double[]{box.minX, box.maxX}) {
         for (double y : new double[]{box.minY, box.maxY}) {
            for (double z : new double[]{box.minZ, box.maxZ}) {
               if (new Vec3(x, y, z).subtract(camera).dot(view) > -1.0) {
                  return false;
               }
            }
         }
      }

      return true;
   }
}
