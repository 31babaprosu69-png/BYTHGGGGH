package pro.webhm.playerinteractions.npc;

import java.util.ArrayList;
import java.util.List;

public final class AppearancePresets {
   private AppearancePresets() {
   }

   public static List<HeadDecorationData> standardHorns() {
      List<HeadDecorationData> parts = new ArrayList<>();
      parts.add(block(-2.63F, -4.25F, -4.38F, 2.18F, 2.03F, 2.03F, -85.08F, -10.55F, -92.11F, 0, 101, 24, 70));
      parts.add(block(2.63F, -4.25F, -4.38F, 2.18F, 2.03F, 2.03F, -85.08F, 10.55F, 92.11F, 0, 101, 24, 70));
      parts.add(block(-2.5F, -4.75F, -5.13F, 1.47F, 2.09F, 1.62F, -156.8F, -9.84F, 2.81F, 8, 101, 24, 70));
      parts.add(block(2.5F, -4.75F, -5.13F, 1.47F, 2.09F, 1.62F, -156.8F, 9.84F, -2.81F, 8, 101, 24, 70));
      parts.add(block(-2.25F, -6.13F, -5.38F, 1.31F, 1.44F, 1.12F, 1.41F, -104.06F, 3.52F, 8, 140, 20, 69));
      parts.add(block(2.25F, -6.25F, -5.25F, 1.31F, 1.44F, 1.12F, 1.41F, 104.06F, -3.52F, 8, 140, 20, 69));
      parts.add(block(-2.13F, -7.25F, -5.13F, 0.63F, 1.16F, 0.97F, -8.44F, -14.77F, 15.47F, 8, 182, 10, 51));
      parts.add(block(2.13F, -7.25F, -5.13F, 0.63F, 1.16F, 0.97F, -8.44F, 14.77F, -15.47F, 8, 182, 10, 51));
      return parts;
   }

   private static HeadDecorationData block(
      float x, float y, float z, float width, float height, float depth, float pitch, float yaw, float roll, int u, int red, int green, int blue
   ) {
      HeadDecorationData d = new HeadDecorationData();
      d.x = x;
      d.y = y;
      d.z = z;
      d.width = width;
      d.height = height;
      d.depth = depth;
      d.pitch = pitch;
      d.yaw = yaw;
      d.roll = roll;
      d.u = u;
      d.v = 8;
      d.solidColor = true;
      d.rgb = red << 16 | green << 8 | blue;
      return d;
   }

   public static void breastPosition(BreastData b, boolean second) {
      b.leftPitch = second ? -7.03F : 0.0F;
      b.leftYaw = second ? -6.33F : 0.0F;
      b.leftRoll = second ? -9.14F : 0.0F;
      b.rightPitch = second ? -9.14F : 0.0F;
      b.rightYaw = second ? 3.52F : 0.0F;
      b.rightRoll = second ? 4.22F : 0.0F;
      b.separation = second ? -0.39F : 0.0F;
   }
}
