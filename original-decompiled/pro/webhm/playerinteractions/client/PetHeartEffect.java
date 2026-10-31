package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class PetHeartEffect {
   private static final int[] COLORS = new int[]{16746170, 13409535, 16754640, 16765324, 9300735, 16098303};

   static void render(PoseStack head, MultiBufferSource buffers, double age, double lastEmission, int seed, float scale, float headWidth, float headHeight) {
      if (!(age < 0.0)) {
         Minecraft mc = Minecraft.getInstance();
         Vector3f center = head.last().pose().transformPosition(new Vector3f(0.0F, -0.25F, 0.0F));
         long newest = (long)Math.floor(age / 42.0);

         for (long index = Math.max(0L, newest - 1L); index <= newest; index++) {
            double born = (double)index * 42.0;
            double ticks = age - born;
            if (!(born > lastEmission) && !(ticks >= 74.0)) {
               float progress = (float)Math.min(1.0, ticks / 64.0);
               int side = (index + (long)seed & 1L) == 0L ? -1 : 1;
               float sway = (float)Math.sin((double)progress * Math.PI * 2.0) * 0.055F;
               float x = (float)side * (0.31F * headWidth + sway);
               float y = 0.36F * headHeight * progress;
               Matrix4f base = new Matrix4f().translation(center).rotate(mc.getEntityRenderDispatcher().cameraOrientation()).scale(scale).translate(x, y, 0.0F);
               int color = COLORS[Math.floorMod((int)index + seed, COLORS.length)];
               float size = 0.017F + (float)Math.floorMod((int)index + seed, 3) * 0.001F;
               if (ticks < 64.0) {
                  float fade = Math.min(1.0F, (float)ticks / 12.0F);
                  float alpha = fade * Math.min(1.0F, (64.0F - (float)ticks) / 6.0F);
                  glyph(buffers, base, "♥", size * (0.85F + 0.15F * fade), color, alpha);
               } else {
                  float t = ((float)ticks - 64.0F) / 10.0F;
                  float radius = size * 1.6F * t;

                  for (int i = 0; i < 6; i++) {
                     double angle = (double)i * Math.PI / 3.0 + (double)index * 0.7;
                     Matrix4f spark = new Matrix4f(base).translate((float)Math.cos(angle) * radius, (float)Math.sin(angle) * radius, 0.0F);
                     glyph(buffers, spark, "·", size * 0.24F, COLORS[(i + Math.floorMod(seed, COLORS.length)) % COLORS.length], (1.0F - t) * (1.0F - t));
                  }
               }
            }
         }
      }
   }

   private static void glyph(MultiBufferSource buffers, Matrix4f at, String text, float size, int color, float alpha) {
      int a = (int)(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
      if (a >= 8) {
         Font font = Minecraft.getInstance().font;
         font.drawInBatch(
            text,
            (float)(-font.width(text)) / 2.0F,
            -4.0F,
            a << 24 | color,
            false,
            new Matrix4f(at).scale(size, -size, size),
            buffers,
            DisplayMode.NORMAL,
            0,
            15728880
         );
      }
   }
}
