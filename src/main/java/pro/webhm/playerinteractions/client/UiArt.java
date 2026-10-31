package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import pro.webhm.playerinteractions.PlayerInteractions;

public final class UiArt {
   private static final ResourceLocation FRAME = PlayerInteractions.id("textures/gui/touch_action_frame.png");
   private static final ResourceLocation PANEL = PlayerInteractions.id("textures/gui/animation_list.png");

   public static void panel(GuiGraphics g, int x, int y, int w, int h) {
      if (h <= 40) {
         bar(g, x, y, w, h);
      } else {
         RenderSystem.enableBlend();
         UiSlices.panel(x, y, w, h, (dx, dy, dw, dh, u, v, sw, sh) -> g.blit(PANEL, dx, dy, dw, dh, (float)u, (float)v, sw, sh, 1402, 1122));
      }
   }

   private static void bar(GuiGraphics g, int x, int y, int w, int h) {
      RenderSystem.enableBlend();
      UiSlices.bar(x, y, w, h, (dx, dy, dw, dh, u, v, sw, sh) -> g.blit(FRAME, dx, dy, dw, dh, (float)u, (float)v, sw, sh, 1983, 793));
   }

   public static void hand(GuiGraphics g, int x, int y, boolean held, boolean pull, int zone) {
      RenderSystem.enableBlend();
      int cell = 627;
      int index = !held ? 0 : (zone != 7 && (zone != 0 && zone != 12 || pull) ? (zone != 0 && zone != 12 ? 2 : 3) : 1);
      g.blit(
         PlayerInteractions.id("textures/gui/touch_hands.png"),
         x - 7,
         y - 7,
         14,
         14,
         (float)(index % 2 * cell),
         (float)(index / 2 * cell),
         cell,
         cell,
         1254,
         1254
      );
   }

   public static Button button(Component label, int x, int y, int width, int height, OnPress press) {
      return new Button(x, y, width, height, label, press, supplier -> (MutableComponent)supplier.get()) {
         protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            UiArt.bar(g, this.getX(), this.getY(), this.getWidth(), this.getHeight());
            g.drawCenteredString(
               Minecraft.getInstance().font,
               this.getMessage(),
               this.getX() + this.getWidth() / 2,
               this.getY() + (this.getHeight() - 8) / 2,
               !this.active ? -1720031623 : (this.isHoveredOrFocused() ? -9353 : -599835)
            );
         }
      };
   }

   private UiArt() {
   }
}
