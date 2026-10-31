package pro.webhm.playerinteractions.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** 1.20.1 versions of the 1.21 packed-ARGB vertex / model helpers. */
public final class Vtx {
   public static void vertex(VertexConsumer out, float x, float y, float z, int argb, float u, float v, int overlay, int light, float nx, float ny, float nz) {
      out.vertex(x, y, z, r(argb), g(argb), b(argb), a(argb), u, v, overlay, light, nx, ny, nz);
   }

   public static void render(ModelPart part, PoseStack pose, VertexConsumer out, int light, int overlay, int argb) {
      part.render(pose, out, light, overlay, r(argb), g(argb), b(argb), a(argb));
   }

   public static int argb(float r, float g, float b, float a) {
      return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
   }

   /** 1.21's PoseStack.mulPose(Matrix4f): multiply the pose matrix and keep the normal matrix in sync. */
   public static void mulPose(PoseStack stack, Matrix4f m) {
      stack.mulPoseMatrix(m);
      stack.last().normal().mul(new Matrix3f(m));
   }

   private static int clamp(float f) {
      return Math.max(0, Math.min(255, Math.round(f * 255.0F)));
   }

   private static float r(int c) { return (float)(c >> 16 & 255) / 255.0F; }
   private static float g(int c) { return (float)(c >> 8 & 255) / 255.0F; }
   private static float b(int c) { return (float)(c & 255) / 255.0F; }
   private static float a(int c) { return (float)(c >>> 24 & 255) / 255.0F; }

   private Vtx() {
   }
}
