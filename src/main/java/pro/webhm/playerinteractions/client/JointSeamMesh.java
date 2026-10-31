package pro.webhm.playerinteractions.client;

import pro.webhm.playerinteractions.compat.Vtx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class JointSeamMesh {
   private JointSeamMesh() {
   }

   static void render(
      PoseStack stack,
      VertexConsumer out,
      ModelPart moving,
      float seam,
      float left,
      float right,
      float front,
      float back,
      float u,
      float v,
      float textureWidth,
      int light,
      int overlay,
      int color
   ) {
      Quaternionf q = new Quaternionf().rotationZYX(moving.zRot, moving.yRot, moving.xRot);
      Vector3f[] fixed = new Vector3f[]{
         new Vector3f(right, seam, front), new Vector3f(left, seam, front), new Vector3f(left, seam, back), new Vector3f(right, seam, back)
      };
      Vector3f[] turned = new Vector3f[4];

      for (int i = 0; i < 4; i++) {
         Vector3f p = fixed[i];
         turned[i] = q.transform(new Vector3f(p.x * moving.xScale, 0.0F, p.z * moving.zScale)).add(moving.x, moving.y, moving.z);
      }

      float[] ua = new float[]{u + 4.0F + textureWidth, u + 4.0F, u + 8.0F + textureWidth, u + 8.0F + textureWidth};
      float[] ub = new float[]{u + 4.0F, u, u + 8.0F + 2.0F * textureWidth, u + 4.0F + textureWidth};

      for (int i = 0; i < 4; i++) {
         int j = (i + 1) % 4;
         float span = Math.max(0.5F, Math.min(4.0F, Math.max(fixed[i].distance(turned[i]), fixed[j].distance(turned[j]))));
         float v0 = v - span * 0.5F;
         float v1 = v + span * 0.5F;

         Vector3f outward = switch (i) {
            case 0 -> new Vector3f(0.0F, 0.0F, -1.0F);
            case 1 -> new Vector3f(-1.0F, 0.0F, 0.0F);
            case 2 -> new Vector3f(0.0F, 0.0F, 1.0F);
            default -> new Vector3f(1.0F, 0.0F, 0.0F);
         };
         new Quaternionf().slerp(q, 0.5F).transform(outward);
         triangle(stack, out, fixed[i], fixed[j], turned[j], ua[i], ub[i], ub[i], v0, v0, v1, outward, light, overlay, color);
         triangle(stack, out, fixed[i], turned[j], turned[i], ua[i], ub[i], ua[i], v0, v1, v1, outward, light, overlay, color);
      }
   }

   private static void triangle(
      PoseStack stack,
      VertexConsumer out,
      Vector3f a,
      Vector3f b,
      Vector3f c,
      float ua,
      float ub,
      float uc,
      float va,
      float vb,
      float vc,
      Vector3f outward,
      int light,
      int overlay,
      int color
   ) {
      Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
      if (!(n.lengthSquared() < 1.0E-10F)) {
         n.normalize();
         if (n.dot(outward) < 0.0F) {
            Vector3f swap = b;
            b = c;
            c = swap;
            float uvSwap = ub;
            ub = uc;
            uc = uvSwap;
            uvSwap = vb;
            vb = vc;
            vc = uvSwap;
            n.negate();
         }

         emit(stack, out, a, ua, va, n, light, overlay, color);
         emit(stack, out, b, ub, vb, n, light, overlay, color);
         emit(stack, out, c, uc, vc, n, light, overlay, color);
         emit(stack, out, c, uc, vc, n, light, overlay, color);
      }
   }

   private static void emit(PoseStack stack, VertexConsumer out, Vector3f point, float u, float v, Vector3f n, int light, int overlay, int color) {
      Vector3f p = stack.last().pose().transformPosition(new Vector3f(point).div(16.0F));
      Vector3f normal = stack.last().normal().transform(n.x, n.y, n.z, new Vector3f());
      Vtx.vertex(out, p.x, p.y, p.z, color, u / 64.0F, v / 64.0F, overlay, light, normal.x, normal.y, normal.z);
   }
}
