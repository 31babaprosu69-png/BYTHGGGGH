package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import pro.webhm.playerinteractions.PlayerInteractions;
import pro.webhm.playerinteractions.WheelActions;
import pro.webhm.playerinteractions.WheelHold;

public final class WheelScreen extends Screen {
   private final int target;
   private final String name;
   private final long opened = System.currentTimeMillis();
   private final WheelHold hold = new WheelHold();

   public WheelScreen(int target, String name) {
      super(InteractionClient.text("wheel"));
      this.target = target;
      this.name = name;
   }

   protected void init() {
   }

   private int size() {
      return Math.min(220, (int)((double)Math.min(this.width, this.height) * 0.66));
   }

   private int sector(double x, double y) {
      return WheelHold.sector(x, y, (double)this.width / 2.0, (double)this.height / 2.0, this.size());
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      int cx = this.width / 2;
      int cy = this.height / 2;
      int size = this.size();
      int hover = this.sector((double)mx, (double)my);
      float appear = Math.min(1.0F, (float)(System.currentTimeMillis() - this.opened) / 220.0F);
      int drawn = (int)((double)size * (1.0 - 0.18 * Math.pow((double)(1.0F - appear), 3.0)));
      RenderSystem.enableBlend();
      g.blit(PlayerInteractions.id("textures/gui/quick_wheel.png"), cx - drawn / 2, cy - drawn / 2, drawn, drawn, 0.0F, 0.0F, 1254, 1254, 1254, 1254);

      for (int i = 0; i < 5; i++) {
         double angle = (-Math.PI / 2) + (double)i * Math.PI * 2.0 / 5.0;
         int x = cx + (int)(Math.cos(angle) * (double)size * 0.33);
         int y = cy + (int)(Math.sin(angle) * (double)size * 0.33);
         int action = WheelActions.at(i);
         Component label = (Component)(action == -1
            ? InteractionClient.text("animations")
            : (action == -2 ? InteractionClient.text("appearance") : InteractionClient.action(action)));
         List<FormattedCharSequence> lines = this.font.split(label, (int)((double)size * 0.27));

         for (int row = 0; row < lines.size(); row++) {
            g.drawCenteredString(this.font, lines.get(row), x, y + row * 9 - lines.size() * 4, hover == i ? -9353 : -599835);
         }
      }

      g.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.name, size / 3), cx, cy - 4, -11039);
      g.drawCenteredString(this.font, InteractionClient.text("consent_hint", PlayerKeys.OPEN.getTranslatedKeyMessage()), cx, cy + size / 2 + 10, -2377784);
      super.render(g, mx, my, partial);
   }

   private void select(int sector) {
      PlayerKeys.drainOpen();
      int action = WheelActions.at(sector);
      if (action == -1) {
         this.minecraft.setScreen(new AnimationScreen(this, this.target));
      } else if (action == -2) {
         this.minecraft.setScreen(new AppearanceScreen(this));
      } else {
         InteractionClient.request(this.target, action);
         this.onClose();
      }
   }

   public boolean mouseClicked(double x, double y, int button) {
      return true;
   }

   public boolean keyPressed(int key, int scan, int mods) {
      return PlayerKeys.OPEN.matches(key, scan) ? true : super.keyPressed(key, scan, mods);
   }

   public void tick() {
      Entity entity = this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.target);
      if (entity != null
         && this.minecraft.player != null
         && entity.isAlive()
         && !(this.minecraft.player.distanceToSqr(entity) > 25.0)
         && this.minecraft.isWindowActive()
         && InteractionClient.local() == null
         && InteractionClient.invitation == null) {
         double x = this.minecraft.mouseHandler.xpos() * (double)this.width / (double)this.minecraft.getWindow().getScreenWidth();
         double y = this.minecraft.mouseHandler.ypos() * (double)this.height / (double)this.minecraft.getWindow().getScreenHeight();
         int choice = this.hold.release(PlayerKeys.openDown(), this.sector(x, y));
         if (choice == -1) {
            this.onClose();
         } else if (choice != -2) {
            this.select(choice);
         }
      } else {
         this.onClose();
      }
   }

   public void onClose() {
      this.hold.cancel();
      PlayerKeys.drainOpen();
      super.onClose();
   }

   public void renderBackground(GuiGraphics g, int x, int y, float dt) {
   }

   public boolean isPauseScreen() {
      return false;
   }
}
