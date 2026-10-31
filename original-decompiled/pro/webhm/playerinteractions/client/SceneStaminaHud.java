package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import pro.webhm.playerinteractions.PlayerInteractions;
import pro.webhm.playerinteractions.SessionState;

final class SceneStaminaHud {
   static void render(GuiGraphics g, float partial) {
      Minecraft mc = Minecraft.getInstance();
      SessionState state = InteractionClient.local();
      if (mc.player != null
         && mc.level != null
         && !mc.options.hideGui
         && mc.screen == null
         && state != null
         && state.loop != null
         && !state.loop.continuous
         && (state.joinTick() < 0 || state.joinComplete)) {
         int panelWidth = 54;
         int panelHeight = Math.min(162, mc.getWindow().getGuiScaledHeight() - 48);
         if (panelHeight >= 60) {
            int x = mc.getWindow().getGuiScaledWidth() - panelWidth - 4;
            int y = (mc.getWindow().getGuiScaledHeight() - panelHeight) / 2 - 8;
            RenderSystem.enableBlend();
            g.blit(PlayerInteractions.id("textures/gui/scene_stamina.png"), x, y, panelWidth, panelHeight, 0.0F, 0.0F, 724, 2172, 724, 2172);
            int left = x + 21;
            int right = x + 33;
            int top = y + panelHeight * 14 / 100;
            int bottom = y + panelHeight * 85 / 100;
            float visual = state.visualStamina((double)mc.level.getGameTime() + (double)partial);
            int filled = Math.round((float)(bottom - top) * visual / 12.0F);
            g.fill(left, top, right, bottom, -14546671);
            if (filled > 0) {
               g.fill(left, bottom - filled, right, bottom, -3664067);
               g.fill(left + 1, bottom - filled, left + 3, bottom, -565897);
               g.fill(right - 2, bottom - filled, right, bottom, -8777687);
            }

            for (int i = 1; i < 12; i++) {
               int tick = bottom - (bottom - top) * i / 12;
               g.fill(left, tick, right, tick + 1, -1724905193);
            }

            float time = (float)mc.level.getGameTime() + partial;
            g.enableScissor(x + 1, Math.max(0, top - 20), x + panelWidth - 1, bottom + 3);

            for (int i = 0; i < 8; i++) {
               float pulse = 0.5F + 0.5F * (float)Math.sin((double)(time * 0.18F + (float)i * 1.7F));
               staminaParticle(g, (left + right) / 2 - 3, top + (bottom - top - 6) * i / 7, false, (int)(45.0F + pulse * 115.0F) << 24 | 16745129);
            }

            if (filled > 0) {
               for (int i = 0; i < 16; i++) {
                  float phase = (time * 0.018F + (float)i / 16.0F) % 1.0F;
                  int py = bottom - 5 - Math.round(phase * (float)Math.max(3, filled - 4));
                  int px = left - 7 + i % 3 * 8 + Math.round((float)Math.sin((double)(time * 0.09F + (float)i)) * 3.0F);
                  int alpha = (int)(110.0 + 140.0 * Math.sin(Math.PI * (double)phase));
                  staminaParticle(g, px, py, false, alpha << 24 | 16751549);
               }
            }

            if (state.loop.celebrationAt >= 0L) {
               float age = time - (float)state.loop.celebrationAt;

               for (int i = 0; i < 20; i++) {
                  float travel = (age - (float)(i % 5) * 0.65F) / 9.0F;
                  if (!(travel < 0.0F) && !(travel > 2.3F)) {
                     float burst = Math.max(0.0F, travel - 1.0F);
                     int px = (left + right) / 2 - 3 + (i % 5 - 2) * 2 + Math.round((float)(i % 7 - 3) * burst * 5.0F);
                     int py = bottom - 6 - Math.round(Math.min(1.0F, travel) * (float)(bottom - top - 3)) + Math.round(-14.0F * burst + 18.0F * burst * burst);
                     int alpha = (int)(255.0F * Math.max(0.0F, 1.0F - burst / 1.3F));
                     staminaParticle(g, px, py, i % 2 == 0, alpha << 24 | (i % 2 == 0 ? 16771237 : 16757972));
                  }
               }
            }

            g.disableScissor();
            String count = state.loop.count + "/12";
            g.drawString(mc.font, count, x + (panelWidth - mc.font.width(count)) / 2, y + panelHeight + 1, -13356, true);
            MutableComponent hint = InteractionClient.text(
               state.loop.count >= 12
                  ? (state.loop.phase == 4 ? "loop.finale" : "loop.ready")
                  : (
                     state.paused
                        ? "loop.paused"
                        : (state.loop.ready() ? (state.initiator().id.equals(mc.player.getUUID()) ? "loop.click" : "loop.wait") : "loop.repeat")
                  )
            );
            g.drawString(mc.font, hint, x + (panelWidth - mc.font.width(hint)) / 2, y + panelHeight + 12, -30562, true);
         }
      }
   }

   private static void staminaParticle(GuiGraphics g, int x, int y, boolean star, int color) {
      if (star) {
         g.fill(x + 3, y, x + 4, y + 7, color);
         g.fill(x, y + 3, x + 7, y + 4, color);
         g.fill(x + 2, y + 2, x + 5, y + 5, color);
      } else {
         g.fill(x + 1, y, x + 3, y + 1, color);
         g.fill(x + 4, y, x + 6, y + 1, color);
         g.fill(x, y + 1, x + 7, y + 3, color);
         g.fill(x + 1, y + 3, x + 6, y + 4, color);
         g.fill(x + 2, y + 4, x + 5, y + 5, color);
         g.fill(x + 3, y + 5, x + 4, y + 6, color);
      }
   }

   private SceneStaminaHud() {
   }
}
