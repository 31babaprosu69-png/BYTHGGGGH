package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import pro.webhm.playerinteractions.AnimationCatalog;
import pro.webhm.playerinteractions.Network;
import pro.webhm.playerinteractions.PlayerInteractions;
import pro.webhm.playerinteractions.anim.AnimationMenu;

public final class AnimationScreen extends Screen {
   private final WheelScreen parent;
   private final int target;
   private int x;
   private int y;
   private int w;
   private int h;
   private int rowHeight;
   private int listTop;
   private int listBottom;
   private int scroll;
   private int seenRevision = -1;
   private boolean dragging;
   private List<Integer> actions = List.of();

   public AnimationScreen(WheelScreen parent, int target) {
      super(InteractionClient.text("animations"));
      this.parent = parent;
      this.target = target;
   }

   protected void init() {
      this.parent.init(this.minecraft, this.width, this.height);
      this.w = Math.min(356, this.width - 16);
      this.h = Math.min(284, this.height - 16);
      this.x = (this.width - this.w) / 2;
      this.y = (this.height - this.h) / 2;
      this.listTop = this.y + 40;
      this.rowHeight = Math.max(10, (this.h - 96) / 10);
      this.listBottom = this.listTop + this.rowHeight * 10;
      this.dragging = false;
      this.rebuild();
      if (!AnimationLibrary.ready()) {
         InteractionClient.send(8, Network.NONE, 0, 0, 0.0F, 0.0F);
      }
   }

   private void rebuild() {
      this.clearWidgets();
      this.seenRevision = AnimationLibrary.revision();
      this.actions = AnimationMenu.actions(AnimationLibrary.entries());
      this.scroll = AnimationMenu.clamp(this.scroll, this.actions.size());

      for (int row = 0; row < 10 && this.scroll + row < this.actions.size(); row++) {
         int action = this.actions.get(this.scroll + row);
         Component name = InteractionClient.action(action);
         int available = this.w - 76;
         String label = this.font.width(name) > available ? this.font.plainSubstrByWidth(name.getString(), available - 10) + "…" : name.getString();
         var button = this.addRenderableWidget(
            new Button(this.x + 26, this.listTop + row * this.rowHeight, this.w - 62, this.rowHeight, Component.literal(label), b -> {
               InteractionClient.request(this.target, action);
               this.minecraft.setScreen(null);
            }, supplier -> (MutableComponent)supplier.get()) {
               protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
                  if (this.isHoveredOrFocused()) {
                     g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight() - 1, 1430198324);
                     g.fill(this.getX(), this.getY() + 2, this.getX() + 2, this.getY() + this.getHeight() - 3, -1396323);
                  }

                  g.drawCenteredString(
                     AnimationScreen.this.font,
                     this.getMessage(),
                     this.getX() + this.getWidth() / 2,
                     this.getY() + (this.getHeight() - 8) / 2,
                     this.isHoveredOrFocused() ? -9353 : -599835
                  );
               }
            }
         );
         button.setTooltip(Tooltip.create(name));
      }

      int bw = (this.w - 60) / 3;
      int by = this.y + this.h - 34;
      this.addRenderableWidget(UiArt.button(InteractionClient.text("catalog.back"), this.x + 26, by, bw, 16, b -> this.onClose()));
      Button reload = (Button)this.addRenderableWidget(
         UiArt.button(InteractionClient.text("catalog.refresh"), this.x + 30 + bw, by, bw, 16, b -> InteractionClient.send(8, Network.NONE, 0, 1, 0.0F, 0.0F))
      );
      reload.setTooltip(Tooltip.create(InteractionClient.text(AnimationLibrary.editable() ? "catalog.reload_hint" : "catalog.server_hint")));
      Button folder = (Button)this.addRenderableWidget(
         UiArt.button(
            InteractionClient.text("catalog.folder"), this.x + 34 + 2 * bw, by, bw, 16, b -> Util.getPlatform().openFile(AnimationCatalog.directory().toFile())
         )
      );
      folder.setTooltip(Tooltip.create(Component.literal("mods/aot_player_interactions/animations")));
   }

   private int thumbHeight() {
      return Math.max(12, (this.listBottom - this.listTop) * 10 / Math.max(10, this.actions.size()));
   }

   private int thumbTop() {
      return this.listTop + (this.listBottom - this.listTop - this.thumbHeight()) * this.scroll / Math.max(1, this.actions.size() - 10);
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      g.fill(0, 0, this.width, this.height, 1426063360);
      RenderSystem.enableBlend();
      g.blit(PlayerInteractions.id("textures/gui/animation_list.png"), this.x, this.y, this.w, this.h, 0.0F, 0.0F, 1402, 1122, 1402, 1122);
      g.drawCenteredString(this.font, this.title, this.x + this.w / 2, this.y + 21, -11372);

      for (int row = 0; row < 10; row++) {
         g.fill(this.x + 28, this.listTop + (row + 1) * this.rowHeight - 1, this.x + this.w - 40, this.listTop + (row + 1) * this.rowHeight, 1144988472);
      }

      if (this.actions.size() > 10) {
         int sx = this.x + this.w - 29;
         g.fill(sx, this.listTop, sx + 3, this.listBottom, -14085601);
         g.fill(sx, this.thumbTop(), sx + 3, this.thumbTop() + this.thumbHeight(), -3830882);
      }

      Component status = AnimationLibrary.failed()
         ? InteractionClient.text("catalog.failed")
         : (
            !AnimationLibrary.ready()
               ? InteractionClient.text("catalog.loading")
               : (
                  AnimationLibrary.skipped() > 0
                     ? InteractionClient.text("catalog.skipped", AnimationLibrary.skipped())
                     : Component.literal(
                        this.actions.isEmpty()
                           ? "0 / 0"
                           : this.scroll + 1 + "–" + Math.min(this.actions.size(), this.scroll + 10) + " / " + this.actions.size()
                     )
               )
         );
      float fit = Math.min(1.0F, (float)(this.w - 60) / (float)Math.max(1, this.font.width(status)));
      g.pose().pushPose();
      g.pose().translate((float)this.x + (float)this.w / 2.0F, (float)(this.y + this.h - 47), 0.0F);
      g.pose().scale(fit, fit, 1.0F);
      g.drawCenteredString(this.font, status, 0, 0, AnimationLibrary.failed() ? -19042 : -3426607);
      g.pose().popPose();
      super.render(g, mx, my, partial);
   }

   private boolean inList(double mx, double my) {
      return mx >= (double)(this.x + 22) && mx < (double)(this.x + this.w - 22) && my >= (double)this.listTop && my < (double)this.listBottom;
   }

   public boolean mouseScrolled(double mx, double my, double dy) {
      if (this.inList(mx, my) && dy != 0.0) {
         this.scroll = AnimationMenu.clamp(this.scroll - (int)Math.signum(dy), this.actions.size());
         this.rebuild();
         return true;
      } else {
         return super.mouseScrolled(mx, my, dy);
      }
   }

   private void dragTo(double my) {
      this.scroll = AnimationMenu.fromTrack(
         (my - (double)this.listTop - (double)this.thumbHeight() / 2.0) / (double)Math.max(1, this.listBottom - this.listTop - this.thumbHeight()),
         this.actions.size()
      );
      this.rebuild();
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0
         && this.actions.size() > 10
         && mx >= (double)(this.x + this.w - 34)
         && mx < (double)(this.x + this.w - 22)
         && my >= (double)this.listTop
         && my < (double)this.listBottom) {
         this.dragging = true;
         this.dragTo(my);
         return true;
      } else {
         return super.mouseClicked(mx, my, button);
      }
   }

   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.dragging && button == 0) {
         this.dragTo(my);
         return true;
      } else {
         return super.mouseDragged(mx, my, button, dx, dy);
      }
   }

   public boolean mouseReleased(double mx, double my, int button) {
      if (button == 0 && this.dragging) {
         this.dragging = false;
         return true;
      } else {
         return super.mouseReleased(mx, my, button);
      }
   }

   public void tick() {
      Entity entity = this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.target);
      if (entity != null && this.minecraft.player != null && !(this.minecraft.player.distanceToSqr(entity) > 25.0)) {
         if (this.seenRevision != AnimationLibrary.revision()) {
            this.rebuild();
         }
      } else {
         this.minecraft.setScreen(null);
      }
   }

   public void onClose() {
      this.parent.onClose();
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (PlayerKeys.OPEN.matches(key, scan)) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   public void renderBackground(GuiGraphics g, int x, int y, float dt) {
   }

   public boolean isPauseScreen() {
      return false;
   }
}
