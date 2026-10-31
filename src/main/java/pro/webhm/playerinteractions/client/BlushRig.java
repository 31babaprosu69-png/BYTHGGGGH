package pro.webhm.playerinteractions.client;

import pro.webhm.playerinteractions.compat.Vtx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.npc.BlushData;
import pro.webhm.playerinteractions.npc.HeadDynamicsData;

public final class BlushRig {
   public static float opacity(BlushData d, BlushData.Region r, HeadDynamicsData head, float x, float y, float amount) {
      if (d.enabled && !(x <= -3.98F) && !(x >= 3.98F) && !(y <= 0.0F) && !(y >= 7.98F)) {
         float eyeBottom = head.eyeY + head.eyeHeight + 0.08F;
         float eyeFade = Math.max(0.0F, Math.min(1.0F, (y - eyeBottom) / 0.2F));
         float dx = (x - r.x) * 2.0F / r.width;
         float dy = (y - r.y) * 2.0F / r.height;
         if (!(Math.abs(dx) >= 1.0F) && !(Math.abs(dy) >= 1.0F)) {
            float[] centers = new float[]{-0.72F, -0.37F, 0.0F, 0.37F, 0.7F};
            float[] offsets = new float[]{0.12F, -0.1F, 0.08F, -0.16F, 0.02F};
            float[] lengths = new float[]{0.32F, 0.52F, 0.68F, 0.49F, 0.29F};
            float stripe = 0.0F;

            for (int i = 0; i < 5; i++) {
               float along = dy - offsets[i];
               float distance = Math.abs(dx - centers[i] + along * 0.6F);
               float cross = 1.0F - smooth(0.018F, 0.045F, distance);
               float tip = 1.0F - smooth(lengths[i] * 0.65F, lengths[i], Math.abs(along));
               stripe = Math.max(stripe, cross * tip);
            }

            return Math.min(1.0F, d.strength * amount * eyeFade * 0.5F * stripe * Math.max(1.0F, Math.min(4.0F, d.colorBoost)));
         } else {
            return 0.0F;
         }
      } else {
         return 0.0F;
      }
   }

   private static float smooth(float a, float b, float x) {
      float t = Math.max(0.0F, Math.min(1.0F, (x - a) / (b - a)));
      return t * t * (3.0F - 2.0F * t);
   }

   public static void render(PoseStack stack, VertexConsumer out, int light, HeadDynamicsData head, float amount) {
      BlushData d = head.blush;
      if (d.enabled && !(amount < 0.001F)) {
         for (BlushData.Region r : new BlushData.Region[]{d.left, d.right}) {
            for (int row = 0; row < 48; row++) {
               for (int col = 0; col < 96; col++) {
                  int[][] corners = new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}};

                  for (int[] c : corners) {
                     float x = r.x - r.width / 2.0F + r.width * (float)(col + c[0]) / 96.0F;
                     float y = r.y - r.height / 2.0F + r.height * (float)(row + c[1]) / 48.0F;
                     int alpha = Math.round(255.0F * opacity(d, r, head, x, y, amount));
                     Vector3f p = stack.last().pose().transformPosition(x / 16.0F, (y - 8.0F) / 16.0F, -0.2546875F, new Vector3f());
                     Vector3f n = stack.last().normal().transform(0.0F, 0.0F, -1.0F, new Vector3f());
                     Vtx.vertex(out, p.x, p.y, p.z, alpha << 24 | d.color & 16777215, 0.5F, 0.5F, OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z);
                  }
               }
            }
         }
      }
   }

   private BlushRig() {
   }
}
