package pro.webhm.playerinteractions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CarryPlacement {
   private static final double[][] OFFSETS = new double[][]{
      {0.0, 0.0}, {0.9, 0.0}, {-0.9, 0.0}, {0.0, 0.9}, {0.0, -0.9}, {0.9, 0.9}, {-0.9, 0.9}, {0.9, -0.9}, {-0.9, -0.9}
   };

   public static AABB body(Vec3 at) {
      return new AABB(at.x - 0.9, at.y + 0.5, at.z - 0.9, at.x + 0.9, at.y + 1.7, at.z + 0.9);
   }

   public static AABB swept(Vec3 from, Vec3 to) {
      return body(from).minmax(body(to));
   }

   public static Vec3 ground(Vec3 near, EntityDimensions dimensions, Function<AABB, List<AABB>> blocks, Predicate<AABB> clear) {
      for (double[] offset : OFFSETS) {
         double x = near.x + offset[0];
         double z = near.z + offset[1];
         double half = (double)dimensions.width / 2.0;
         AABB column = new AABB(x - half, near.y - 2.01, z - half, x + half, near.y + 1.01, z + half);
         ArrayList<AABB> surfaces = new ArrayList<>(blocks.apply(column));
         surfaces.sort(Comparator.comparingDouble(b -> Math.abs(b.maxY - near.y)));

         for (AABB surface : surfaces) {
            if (!(surface.maxY < near.y - 2.0)
               && !(surface.maxY > near.y + 1.0)
               && !(surface.maxX <= column.minX)
               && !(surface.minX >= column.maxX)
               && !(surface.maxZ <= column.minZ)
               && !(surface.minZ >= column.maxZ)) {
               Vec3 at = new Vec3(x, surface.maxY, z);
               if (clear.test(dimensions.makeBoundingBox(at))) {
                  return at;
               }
            }
         }
      }

      return null;
   }

   public static Vec3 free(Vec3 near, EntityDimensions dimensions, Predicate<AABB> clear) {
      for (double dy : new double[]{0.0, 0.5, 1.0, 1.5, 2.0}) {
         for (double[] offset : OFFSETS) {
            Vec3 at = near.add(offset[0], dy, offset[1]);
            if (clear.test(dimensions.makeBoundingBox(at))) {
               return at;
            }
         }
      }

      return null;
   }

   private CarryPlacement() {
   }
}
