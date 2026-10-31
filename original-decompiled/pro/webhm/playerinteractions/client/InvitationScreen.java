package pro.webhm.playerinteractions.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pro.webhm.playerinteractions.InteractionServer;

public final class InvitationScreen extends Screen {
   public InvitationScreen() {
      super(InteractionClient.text("invitation"));
   }

   protected void init() {
      InteractionServer.Invitation invite = InteractionClient.invitation;
      if (invite == null) {
         this.onClose();
      } else {
         int cy = this.height / 2;
         if (invite.incoming()) {
            this.addRenderableWidget(UiArt.button(InteractionClient.text("accept"), this.width / 2 - 120, cy + 34, 114, 22, b -> {
               InteractionClient.answer(true);
               this.minecraft.setScreen(null);
            }));
            this.addRenderableWidget(UiArt.button(InteractionClient.text("decline"), this.width / 2 + 6, cy + 34, 114, 22, b -> {
               InteractionClient.answer(false);
               this.minecraft.setScreen(null);
            }));
         } else {
            this.addRenderableWidget(UiArt.button(InteractionClient.text("cancel"), this.width / 2 - 60, cy + 34, 120, 22, b -> {
               InteractionClient.stop();
               this.minecraft.setScreen(null);
            }));
         }
      }
   }

   public void render(GuiGraphics g, int x, int y, float dt) {
      InteractionServer.Invitation invite = InteractionClient.invitation;
      if (invite != null) {
         g.fill(0, 0, this.width, this.height, -2062480358);
         int cx = this.width / 2;
         int cy = this.height / 2;
         int w = Math.min(310, this.width - 12);
         UiArt.panel(g, cx - w / 2, cy - 65, w, 135);
         g.drawCenteredString(this.font, invite.incoming() ? invite.fromName() : invite.toName(), cx, cy - 30, -10856);
         Component action = InteractionClient.action(invite.invite().action());
         float fit = Math.min(1.0F, (float)(w - 32) / (float)Math.max(1, this.font.width(action)));
         g.pose().pushPose();
         g.pose().translate((float)cx, (float)(cy - 15), 0.0F);
         g.pose().scale(fit, fit, 1.0F);
         g.drawCenteredString(this.font, action, 0, 0, -6417);
         g.pose().popPose();
         g.drawCenteredString(this.font, InteractionClient.text(invite.incoming() ? "confirm_text" : "waiting_text"), cx, cy + 1, -3360049);
         long seconds = Math.max(0L, (invite.invite().expires() - this.minecraft.level.getGameTime() + 19L) / 20L);
         g.drawCenteredString(this.font, InteractionClient.text("expires", seconds), cx, cy + 16, -5465930);
         super.render(g, x, y, dt);
      }
   }

   public void onClose() {
      InteractionServer.Invitation invite = InteractionClient.invitation;
      if (invite != null) {
         if (invite.incoming()) {
            InteractionClient.answer(false);
         } else {
            InteractionClient.stop();
         }
      }

      super.onClose();
   }

   public void tick() {
      if (InteractionClient.invitation == null) {
         this.minecraft.setScreen(null);
      }
   }

   public void renderBackground(GuiGraphics g, int x, int y, float dt) {
   }

   public boolean isPauseScreen() {
      return false;
   }
}
