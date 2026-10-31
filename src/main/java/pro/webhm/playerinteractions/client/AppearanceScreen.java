package pro.webhm.playerinteractions.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import pro.webhm.playerinteractions.Appearance;

public final class AppearanceScreen extends Screen {
   private final Screen parent;
   private boolean initialized;
   private boolean breastEnabled;
   private int arms;
   private int left;
   private int top;
   private int panelWidth;
   private int panelHeight;
   private int controlX;
   private int controlWidth;

   public AppearanceScreen(Screen parent) {
      super(InteractionClient.text("appearance"));
      this.parent = parent;
   }

   protected void init() {
      if (this.minecraft.player == null) {
         this.onClose();
      } else {
         if (!this.initialized) {
            this.breastEnabled = AppearanceClient.get(this.minecraft.player.getUUID()).enabled;
            this.arms = AppearanceClient.arms(this.minecraft.player.getUUID());
            this.initialized = true;
         }

         this.panelWidth = Math.min(300, this.width - 12);
         this.panelHeight = Math.min(174, this.height - 12);
         this.left = (this.width - this.panelWidth) / 2;
         this.top = (this.height - this.panelHeight) / 2;
         this.controlWidth = Math.min(166, this.panelWidth - 114);
         this.controlX = this.left + 100 + (this.panelWidth - 114 - this.controlWidth) / 2;
         this.addRenderableWidget(UiArt.button(this.presetLabel(), this.controlX, this.top + 42, this.controlWidth, 18, b -> {
            this.breastEnabled = !this.breastEnabled;
            b.setMessage(this.presetLabel());
         }));
         this.addRenderableWidget(
            UiArt.button(InteractionClient.text("appearance.arms." + this.arms), this.controlX, this.top + 72, this.controlWidth, 18, b -> {
               this.arms = (this.arms + 1) % 3;
               b.setMessage(InteractionClient.text("appearance.arms." + this.arms));
            })
         );
         int w = Math.min(100, (this.panelWidth - 36) / 2);
         int buttonsX = this.left + (this.panelWidth - 2 * w - 12) / 2;
         this.addRenderableWidget(UiArt.button(InteractionClient.text("appearance.save"), buttonsX, this.top + this.panelHeight - 28, w, 18, b -> {
            AppearanceClient.save(Appearance.selected(this.breastEnabled), this.arms);
            this.onClose();
         }));
         this.addRenderableWidget(
            UiArt.button(InteractionClient.text("appearance.cancel"), buttonsX + w + 12, this.top + this.panelHeight - 28, w, 18, b -> this.onClose())
         );
      }
   }

   private Component presetLabel() {
      return InteractionClient.text(this.breastEnabled ? "appearance.preset_on" : "appearance.preset_off");
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      UiArt.panel(g, this.left, this.top, this.panelWidth, this.panelHeight);
      g.drawCenteredString(this.font, this.title, this.width / 2, this.top + 12, -10307);
      if (this.minecraft.player != null) {
         AppearanceClient.preview = Appearance.selected(this.breastEnabled);
         AppearanceClient.previewArms = this.arms;

         try {
            int previewX = this.left + 51;
            int previewY = this.top + 28 + (this.panelHeight - 68) / 2 + 29;
            InventoryScreen.renderEntityInInventoryFollowsMouse(
               g, previewX, previewY, 32, (float)(previewX - mx), (float)(previewY - 50 - my), this.minecraft.player
            );
         } finally {
            AppearanceClient.preview = null;
            AppearanceClient.previewArms = null;
         }
      }

      int y = this.top + 101;

      for (FormattedCharSequence line : this.font.split(InteractionClient.text("appearance.preset_hint"), this.controlWidth)) {
         g.drawString(this.font, line, this.controlX, y, -3296315);
         y += 10;
      }

      super.render(g, mx, my, partial);
   }

   public void removed() {
      AppearanceClient.preview = null;
      AppearanceClient.previewArms = null;
      super.removed();
   }

   public void onClose() {
      AppearanceClient.preview = null;
      AppearanceClient.previewArms = null;
      if (this.parent instanceof WheelScreen) {
         this.parent.onClose();
      } else {
         this.minecraft.setScreen(this.parent);
      }
   }

   public void tick() {
      if (this.minecraft.player == null || this.minecraft.level == null) {
         this.minecraft.setScreen(null);
      }
   }

   public void renderBackground(GuiGraphics g, int x, int y, float partial) {
   }

   public boolean isPauseScreen() {
      return false;
   }
}
