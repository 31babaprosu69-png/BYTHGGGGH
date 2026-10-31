package pro.webhm.playerinteractions.facial;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

public final class FacialHandMesh {
   public static void render(PoseStack s, VertexConsumer out, int light, FacialData d, FacialPose pose, int zone, int side, float progress, float dx, float dy) {
      render(s, out, light, d, pose, zone, side, progress, dx, dy, (float)(side < 0 ? -1 : 1) * 2.65F, -2.7F);
   }

   public static void render(
      PoseStack s,
      VertexConsumer out,
      int light,
      FacialData d,
      FacialPose pose,
      int zone,
      int side,
      float progress,
      float dx,
      float dy,
      float anchorX,
      float anchorY
   ) {
      float t = FacialData.clamp(progress, 0.0F, 1.0F);
      if (!(t < 0.005F)) {
         boolean mouth = zone == 11;
         boolean stroke = zone == 12;
         float sign = side < 0 ? -1.0F : 1.0F;
         float x = mouth ? d.x : anchorX + FacialData.clamp(dx, -0.35F, 0.35F);
         float y = mouth ? (FacialMesh.upper(d.x, d, pose) + FacialMesh.lower(d.x, d, pose)) * 0.5F : anchorY + FacialData.clamp(dy, -0.65F, 0.65F);
         float approach = FacialExpression.ease((double)t);
         float away = 1.0F - approach;
         y += away * 5.0F;
         float front = -4.2F - away * 5.0F;
         int skin = -2050148;
         if (mouth) {
            float gap = FacialMesh.lower(d.x, d, pose) - FacialMesh.upper(d.x, d, pose);
            float radius = Math.min(0.14F, Math.max(0.015F, gap * 0.22F));
            float tip = -4.0F + d.depth * 0.45F - away * 5.0F;
            Vector3f end = new Vector3f(x, y, tip);
            Vector3f joint = new Vector3f(x + 0.4F, y + 0.45F, front - 0.9F);
            Vector3f root = new Vector3f(x + 0.9F, y + 1.15F, front - 1.5F);
            segment(s, out, light, skin, root, joint, radius * 1.35F);
            segment(s, out, light, skin, joint, end, radius);
            palm(s, out, light, skin, x + 0.8F, y + 1.5F, front - 1.9F);

            for (int i = 0; i < 4; i++) {
               float fx = x + 0.05F + (float)i * 0.38F;
               Vector3f a = new Vector3f(fx, y + 1.15F, front - 1.9F);
               Vector3f b = new Vector3f(fx, y + 0.55F, front - 1.7F);
               Vector3f c = new Vector3f(fx, y + 0.62F, front - 1.25F);
               segment(s, out, light, skin, a, b, 0.17F);
               segment(s, out, light, skin, b, c, 0.15F);
            }
         } else {
            float px = x + sign * 0.35F;
            float py = y + 1.25F;
            palm(s, out, light, skin, px, py, front - 0.48F);

            for (int i = 0; i < 4; i++) {
               float fx = px + ((float)i - 1.5F) * 0.36F;
               float length = 1.2F - Math.abs((float)i - 1.4F) * 0.18F;
               Vector3f a = new Vector3f(fx, py - 0.4F, front - 0.45F);
               Vector3f b = new Vector3f(fx, py - 0.4F - length * 0.55F, front - 0.12F);
               Vector3f c = new Vector3f(fx, py - 0.4F - length * (stroke ? 1.0F : 0.35F), front + (stroke ? 0.0F : -0.18F));
               segment(s, out, light, skin, a, b, 0.17F);
               segment(s, out, light, skin, b, c, 0.145F);
            }

            segment(
               s, out, light, skin, new Vector3f(px - sign * 0.58F, py + 0.25F, front - 0.5F), new Vector3f(x - sign * 0.7F, y + 0.25F, front - 0.05F), 0.21F
            );
         }
      }
   }

   private static void palm(PoseStack s, VertexConsumer out, int light, int skin, float x, float y, float z) {
      FacialMesh.box(s, out, light, skin, x - 0.72F, y - 0.55F, z - 0.32F, x + 0.72F, y + 0.8F, z + 0.32F);
      segment(s, out, light, skin, new Vector3f(x, y + 0.65F, z), new Vector3f(x + 0.25F, y + 2.1F, z - 0.65F), 0.49F);
      segment(s, out, light, -12237740, new Vector3f(x + 0.25F, y + 2.0F, z - 0.65F), new Vector3f(x + 0.9F, y + 4.8F, z - 2.2F), 0.55F);
   }

   private static void segment(PoseStack s, VertexConsumer out, int light, int color, Vector3f a, Vector3f b, float radius) {
      Vector3f axis = new Vector3f(b).sub(a).normalize();
      Vector3f u = new Vector3f(axis).cross(Math.abs(axis.y) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
      Vector3f v = new Vector3f(axis).cross(u).normalize();
      Vector3f[] near = new Vector3f[8];
      Vector3f[] far = new Vector3f[8];

      for (int i = 0; i < 8; i++) {
         double angle = ((double)i + 0.5) * Math.PI / 4.0;
         Vector3f delta = new Vector3f(u).mul((float)Math.cos(angle) * radius).fma((float)Math.sin(angle) * radius, v);
         near[i] = new Vector3f(a).add(delta);
         far[i] = new Vector3f(b).add(delta);
      }

      for (int i = 0; i < 8; i++) {
         int j = (i + 1) % 8;
         FacialMesh.quad(s, out, light, color, near[i], near[j], far[j], far[i]);
         FacialMesh.quad(s, out, light, color, a, near[j], near[i], a);
         FacialMesh.quad(s, out, light, color, b, far[i], far[j], b);
      }
   }

   private FacialHandMesh() {
   }
}
