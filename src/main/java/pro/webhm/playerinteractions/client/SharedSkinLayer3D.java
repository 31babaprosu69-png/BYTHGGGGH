package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.npc.BodyData;
import pro.webhm.playerinteractions.npc.NpcBone;

public final class SharedSkinLayer3D {
   private static final int WHITE = -1;
   private static final float BASE_EPSILON = 0.002F;

   public static void render(PoseStack pose, MultiBufferSource buffers, int light, BodyData data, SharedBodyModel<?> model) {
      ResourceLocation texture = SkinCache.get(data.skinUrl);
      VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture, true));
      render(pose, model.strokeBuffer(pose, out), light, data, model);
   }

   public static void render(PoseStack pose, VertexConsumer out, int light, BodyData data, SharedBodyModel<?> model) {
      int textureScale = SkinCache.skinScale(data.skinUrl);
      if (!data.facial.enabled && (data.headDynamics == null || !data.headDynamics.hairEnabled)) {
         part(pose, out, light, model, NpcBone.HEAD, -4.0F, -8.0F, -4.0F, 8, 8, 8, 32, 0, 0, 8, true, true, 0.5F, textureScale);
      }

      if (data.modelShape == null || !data.modelShape.hasCustomTorso() || !data.modelShape.shapeSecondLayer) {
         part(pose, out, light, model, NpcBone.CHEST, -4.0F, -6.0F, -2.0F, 8, 12, 4, 16, 32, 0, 6, true, false, 0.25F, textureScale);
         part(pose, out, light, model, NpcBone.BODY, -4.0F, -6.0F, -2.0F, 8, 12, 4, 16, 32, 6, 6, false, true, 0.25F, textureScale);
      }

      model.renderBreastSkinLayer3D(pose, out, light, OverlayTexture.NO_OVERLAY, -1, textureScale);
   }

   private static void part(
      PoseStack stack,
      VertexConsumer out,
      int light,
      SharedBodyModel<?> model,
      NpcBone bone,
      float x,
      float y,
      float z,
      int width,
      int fullHeight,
      int depth,
      int texU,
      int texV,
      int rowStart,
      int rowCount,
      boolean top,
      boolean bottom,
      float inflate,
      int textureScale
   ) {
      ModelPart part = model.bonePart(bone);
      if (part != null && part.visible && !part.skipDraw) {
         stack.pushPose();
         model.translateToBone(bone, stack);
         renderShell(stack.last(), out, light, x, y, z, width, fullHeight, depth, texU, texV, rowStart, rowCount, top, bottom, inflate, textureScale);
         stack.popPose();
      }
   }

   private static void renderShell(
      Pose pose,
      VertexConsumer out,
      int light,
      float x0,
      float y0,
      float z0,
      int width,
      int height,
      int depth,
      int texU,
      int texV,
      int rowStart,
      int rowCount,
      boolean top,
      boolean bottom,
      float inflate,
      int textureScale
   ) {
      int scale = Math.max(1, textureScale);
      float texel = 1.0F / (float)scale;
      int widthPixels = width * scale;
      int depthPixels = depth * scale;
      int rowPixels = rowCount * scale;
      int rowStartPixels = rowStart * scale;
      SharedSkinLayer3D.Normals normals = new SharedSkinLayer3D.Normals(
         pose.transformNormal(1.0F, 0.0F, 0.0F, new Vector3f()),
         pose.transformNormal(-1.0F, 0.0F, 0.0F, new Vector3f()),
         pose.transformNormal(0.0F, 1.0F, 0.0F, new Vector3f()),
         pose.transformNormal(0.0F, -1.0F, 0.0F, new Vector3f()),
         pose.transformNormal(0.0F, 0.0F, 1.0F, new Vector3f()),
         pose.transformNormal(0.0F, 0.0F, -1.0F, new Vector3f())
      );

      for (int u = 0; u < widthPixels; u++) {
         float ax0 = edgeMin(x0, u, widthPixels, scale, inflate);
         float ax1 = edgeMax(x0, u, widthPixels, scale, inflate);

         for (int row = 0; row < rowPixels; row++) {
            int v = rowStartPixels + row;
            float ay0 = rowMin(y0, row, rowPixels, scale, top, inflate);
            float ay1 = rowMax(y0, row, rowPixels, scale, bottom, inflate);
            voxel(
               out,
               pose.pose(),
               normals,
               light,
               ax0,
               ax1,
               ay0,
               ay1,
               z0 - inflate,
               z0 - 0.002F,
               (float)(texU + depth) + (float)u * texel,
               (float)(texV + depth) + (float)v * texel,
               texel
            );
            int backX = widthPixels - 1 - u;
            float bx0 = edgeMin(x0, backX, widthPixels, scale, inflate);
            float bx1 = edgeMax(x0, backX, widthPixels, scale, inflate);
            voxel(
               out,
               pose.pose(),
               normals,
               light,
               bx0,
               bx1,
               ay0,
               ay1,
               z0 + (float)depth + 0.002F,
               z0 + (float)depth + inflate,
               (float)(texU + depth + width + depth) + (float)u * texel,
               (float)(texV + depth) + (float)v * texel,
               texel
            );
         }
      }

      for (int u = 0; u < depthPixels; u++) {
         int westZ = depthPixels - 1 - u;
         float wz0 = edgeMin(z0, westZ, depthPixels, scale, inflate);
         float wz1 = edgeMax(z0, westZ, depthPixels, scale, inflate);
         float ez0 = edgeMin(z0, u, depthPixels, scale, inflate);
         float ez1 = edgeMax(z0, u, depthPixels, scale, inflate);

         for (int row = 0; row < rowPixels; row++) {
            int v = rowStartPixels + row;
            float ay0 = rowMin(y0, row, rowPixels, scale, top, inflate);
            float ay1 = rowMax(y0, row, rowPixels, scale, bottom, inflate);
            voxel(
               out,
               pose.pose(),
               normals,
               light,
               x0 - inflate,
               x0 - 0.002F,
               ay0,
               ay1,
               wz0,
               wz1,
               (float)texU + (float)u * texel,
               (float)(texV + depth) + (float)v * texel,
               texel
            );
            voxel(
               out,
               pose.pose(),
               normals,
               light,
               x0 + (float)width + 0.002F,
               x0 + (float)width + inflate,
               ay0,
               ay1,
               ez0,
               ez1,
               (float)(texU + depth + width) + (float)u * texel,
               (float)(texV + depth) + (float)v * texel,
               texel
            );
         }
      }

      if (top) {
         for (int u = 0; u < widthPixels; u++) {
            float ax0 = edgeMin(x0, u, widthPixels, scale, inflate);
            float ax1 = edgeMax(x0, u, widthPixels, scale, inflate);

            for (int v = 0; v < depthPixels; v++) {
               int zi = depthPixels - 1 - v;
               float az0 = edgeMin(z0, zi, depthPixels, scale, inflate);
               float az1 = edgeMax(z0, zi, depthPixels, scale, inflate);
               voxel(
                  out,
                  pose.pose(),
                  normals,
                  light,
                  ax0,
                  ax1,
                  y0 - inflate,
                  y0 - 0.002F,
                  az0,
                  az1,
                  (float)(texU + depth) + (float)u * texel,
                  (float)texV + (float)v * texel,
                  texel
               );
            }
         }
      }

      if (bottom) {
         float bottomY = y0 + (float)rowCount;

         for (int u = 0; u < widthPixels; u++) {
            float ax0 = edgeMin(x0, u, widthPixels, scale, inflate);
            float ax1 = edgeMax(x0, u, widthPixels, scale, inflate);

            for (int v = 0; v < depthPixels; v++) {
               int zi = depthPixels - 1 - v;
               float az0 = edgeMin(z0, zi, depthPixels, scale, inflate);
               float az1 = edgeMax(z0, zi, depthPixels, scale, inflate);
               voxel(
                  out,
                  pose.pose(),
                  normals,
                  light,
                  ax0,
                  ax1,
                  bottomY + 0.002F,
                  bottomY + inflate,
                  az0,
                  az1,
                  (float)(texU + width + depth) + (float)u * texel,
                  (float)texV + (float)v * texel,
                  texel
               );
            }
         }
      }
   }

   private static float edgeMin(float base, int index, int count, int scale, float inflate) {
      return base + (float)index / (float)scale - (index == 0 ? inflate : 0.0F);
   }

   private static float edgeMax(float base, int index, int count, int scale, float inflate) {
      return base + ((float)index + 1.0F) / (float)scale + (index == count - 1 ? inflate : 0.0F);
   }

   private static float rowMin(float base, int row, int count, int scale, boolean top, float inflate) {
      return base + (float)row / (float)scale - (top && row == 0 ? inflate : 0.0F);
   }

   private static float rowMax(float base, int row, int count, int scale, boolean bottom, float inflate) {
      return base + ((float)row + 1.0F) / (float)scale + (bottom && row == count - 1 ? inflate : 0.0F);
   }

   private static void voxel(
      VertexConsumer out,
      Matrix4f matrix,
      SharedSkinLayer3D.Normals n,
      int light,
      float x0,
      float x1,
      float y0,
      float y1,
      float z0,
      float z1,
      float u,
      float v,
      float texel
   ) {
      quad(out, matrix, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u, v, texel, n.positiveX);
      quad(out, matrix, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u, v, texel, n.negativeX);
      quad(out, matrix, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u, v, texel, n.positiveY);
      quad(out, matrix, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u, v, texel, n.negativeY);
      quad(out, matrix, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u, v, texel, n.positiveZ);
      quad(out, matrix, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u, v, texel, n.negativeZ);
   }

   private static void quad(
      VertexConsumer out,
      Matrix4f matrix,
      int light,
      float x0,
      float y0,
      float z0,
      float x1,
      float y1,
      float z1,
      float x2,
      float y2,
      float z2,
      float x3,
      float y3,
      float z3,
      float u,
      float v,
      float texel,
      Vector3f normal
   ) {
      vertex(out, matrix, light, x0, y0, z0, u + texel, v, normal);
      vertex(out, matrix, light, x1, y1, z1, u, v, normal);
      vertex(out, matrix, light, x2, y2, z2, u, v + texel, normal);
      vertex(out, matrix, light, x3, y3, z3, u + texel, v + texel, normal);
   }

   private static void vertex(VertexConsumer out, Matrix4f matrix, int light, float x, float y, float z, float u, float v, Vector3f normal) {
      float sx = x / 16.0F;
      float sy = y / 16.0F;
      float sz = z / 16.0F;
      float px = matrix.m00() * sx + matrix.m10() * sy + matrix.m20() * sz + matrix.m30();
      float py = matrix.m01() * sx + matrix.m11() * sy + matrix.m21() * sz + matrix.m31();
      float pz = matrix.m02() * sx + matrix.m12() * sy + matrix.m22() * sz + matrix.m32();
      out.addVertex(px, py, pz, -1, u / 64.0F, v / 64.0F, OverlayTexture.NO_OVERLAY, light, normal.x(), normal.y(), normal.z());
   }

   private static record Normals(Vector3f positiveX, Vector3f negativeX, Vector3f positiveY, Vector3f negativeY, Vector3f positiveZ, Vector3f negativeZ) {
   }
}
