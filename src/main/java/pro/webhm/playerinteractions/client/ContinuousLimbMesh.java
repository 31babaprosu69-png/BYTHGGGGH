package pro.webhm.playerinteractions.client;

import pro.webhm.playerinteractions.compat.Vtx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class ContinuousLimbMesh {
   private ContinuousLimbMesh() {
   }

   static void render(
      PoseStack stack,
      VertexConsumer out,
      ModelPart lower,
      float x0,
      float width,
      float depth,
      float y0,
      float textureWidth,
      int u,
      int v,
      float inflate,
      int light,
      int overlay,
      int color
   ) {
      Quaternionf rotation = new Quaternionf().rotationZYX(lower.zRot, lower.yRot, lower.xRot);
      float seam = y0 + 6.0F;
      JointSeamMesh.render(
         stack,
         out,
         lower,
         seam,
         x0 - inflate,
         x0 + width + inflate,
         -depth * 0.5F - inflate,
         depth * 0.5F + inflate,
         (float)u,
         (float)v + 9.5F,
         textureWidth,
         light,
         overlay,
         color
      );

      for (int half = 0; half < 2; half++) {
         ModelPart bone = half == 0 ? null : lower;
         float left = x0 - inflate;
         float right = x0 + width + inflate;
         float front = -depth * 0.5F - inflate;
         float back = depth * 0.5F + inflate;
         float top = half == 0 ? y0 - inflate : seam;
         float bottom = half == 0 ? seam : y0 + 12.0F + inflate;
         float va = (float)v + 4.0F + (float)half * 6.0F;
         float vb = va + 6.0F;
         quad(
            stack,
            out,
            bone,
            rotation,
            seam,
            light,
            overlay,
            color,
            right,
            top,
            front,
            left,
            top,
            front,
            left,
            bottom,
            front,
            right,
            bottom,
            front,
            (float)(u + 4),
            va,
            (float)(u + 4) + textureWidth,
            vb
         );
         quad(
            stack,
            out,
            bone,
            rotation,
            seam,
            light,
            overlay,
            color,
            left,
            top,
            back,
            right,
            top,
            back,
            right,
            bottom,
            back,
            left,
            bottom,
            back,
            (float)(u + 8) + textureWidth,
            va,
            (float)(u + 8) + 2.0F * textureWidth,
            vb
         );
         quad(
            stack,
            out,
            bone,
            rotation,
            seam,
            light,
            overlay,
            color,
            left,
            top,
            front,
            left,
            top,
            back,
            left,
            bottom,
            back,
            left,
            bottom,
            front,
            (float)u,
            va,
            (float)(u + 4),
            vb
         );
         quad(
            stack,
            out,
            bone,
            rotation,
            seam,
            light,
            overlay,
            color,
            right,
            top,
            back,
            right,
            top,
            front,
            right,
            bottom,
            front,
            right,
            bottom,
            back,
            (float)(u + 4) + textureWidth,
            va,
            (float)(u + 8) + textureWidth,
            vb
         );
         quad(
            stack,
            out,
            bone,
            rotation,
            seam,
            light,
            overlay,
            color,
            right,
            top,
            back,
            left,
            top,
            back,
            left,
            top,
            front,
            right,
            top,
            front,
            (float)(u + 4),
            half == 0 ? (float)v : va,
            (float)(u + 4) + textureWidth,
            half == 0 ? (float)(v + 4) : va + 1.0F
         );
         quad(
            stack,
            out,
            bone,
            rotation,
            seam,
            light,
            overlay,
            color,
            right,
            bottom,
            front,
            left,
            bottom,
            front,
            left,
            bottom,
            back,
            right,
            bottom,
            back,
            half == 0 ? (float)(u + 4) : (float)(u + 4) + textureWidth,
            half == 0 ? vb - 1.0F : (float)(v + 4),
            half == 0 ? (float)(u + 4) + textureWidth : (float)(u + 4) + 2.0F * textureWidth,
            half == 0 ? vb : (float)v
         );
      }
   }

   private static Vector3f point(float x, float y, float z, ModelPart lower, Quaternionf rotation, float seam) {
      if (lower == null) {
         return new Vector3f(x, y, z);
      } else {
         Vector3f p = new Vector3f(x, y - seam, z);
         p.mul(lower.xScale, lower.yScale, lower.zScale);
         return rotation.transform(p).add(lower.x, lower.y, lower.z);
      }
   }

   private static void quad(
      PoseStack stack,
      VertexConsumer out,
      ModelPart lower,
      Quaternionf q,
      float seam,
      int light,
      int overlay,
      int color,
      float ax,
      float ay,
      float az,
      float bx,
      float by,
      float bz,
      float cx,
      float cy,
      float cz,
      float dx,
      float dy,
      float dz,
      float u0,
      float v0,
      float u1,
      float v1
   ) {
      Vector3f a = point(ax, ay, az, lower, q, seam);
      Vector3f b = point(bx, by, bz, lower, q, seam);
      Vector3f c = point(cx, cy, cz, lower, q, seam);
      Vector3f d = point(dx, dy, dz, lower, q, seam);
      Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
      if (!(n.lengthSquared() < 1.0E-12F)) {
         n.normalize();
         emit(stack, out, a, u1, v0, n, light, overlay, color);
         emit(stack, out, b, u0, v0, n, light, overlay, color);
         emit(stack, out, c, u0, v1, n, light, overlay, color);
         emit(stack, out, d, u1, v1, n, light, overlay, color);
      }
   }

   private static void emit(PoseStack stack, VertexConsumer out, Vector3f p, float u, float v, Vector3f n, int light, int overlay, int color) {
      p.div(16.0F);
      stack.last().pose().transformPosition(p);
      Vector3f normal = stack.last().normal().transform(n.x, n.y, n.z, new Vector3f());
      Vtx.vertex(out, p.x, p.y, p.z, color, u / 64.0F, v / 64.0F, overlay, light, normal.x, normal.y, normal.z);
   }
}
