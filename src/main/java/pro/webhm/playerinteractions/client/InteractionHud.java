package pro.webhm.playerinteractions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import pro.webhm.playerinteractions.Actions;
import pro.webhm.playerinteractions.SessionState;
import pro.webhm.playerinteractions.TouchZones;

final class InteractionHud {
   static void render(GuiGraphics g) {
      Minecraft mc = Minecraft.getInstance();
      SessionState s = InteractionClient.local();
      if (InteractionClient.invitation != null || s != null) {
         float scale = Math.min(0.75F, 2.0F / (float)mc.getWindow().getGuiScale());
         int width = (int)((float)mc.getWindow().getGuiScaledWidth() / scale);
         int height = (int)((float)mc.getWindow().getGuiScaledHeight() / scale);
         int cx = width / 2;
         g.pose().pushPose();
         g.pose().scale(scale, scale, 1.0F);

         try {
            if (InteractionClient.invitation != null) {
               MutableComponent who = Component.literal(
                  InteractionClient.invitation.incoming() ? InteractionClient.invitation.fromName() : InteractionClient.invitation.toName()
               );
               MutableComponent title = who.append(" · ").append(InteractionClient.action(InteractionClient.invitation.invite().action()));
               MutableComponent hint = InteractionClient.text(
                  InteractionClient.invitation.incoming() ? "invite_hint" : "waiting", PlayerKeys.OPEN.getTranslatedKeyMessage()
               );
               int w = Math.min(width - 16, Math.max(mc.font.width(title), mc.font.width(hint)) + 34);
               int x = width - w - 8;
               UiArt.panel(g, x, 8, w, 34);
               line(g, title, x + w / 2, 15, w - 28, -11372);
               line(g, hint, x + w / 2, 28, w - 28, -3426607);
               return;
            }

            boolean initiator = s.initiator().id.equals(mc.player.getUUID());
            int bottom = bottom(height, scale, leftHeight(mc), rightHeight(mc));
            if (s.action == 4) {
               TouchZones.Hit hit = InteractionClient.touchHit();
               int zone = s.touching ? s.zone : (hit == null ? -1 : hit.zone());
               boolean cheek = zone == 0 || zone == 12;
               MutableComponent title = InteractionClient.text(
                  initiator ? (cheek ? (s.mode == 0 ? "hud.cheek_stroke" : "hud.cheek_pull") : (zone == 7 ? "hud.head" : "hud.contact")) : "touch_receiving"
               );
               int w = Math.min(width - 16, mc.font.width(title) + 34);
               UiArt.panel(g, cx - w / 2, bottom - 40, w, 20);
               line(g, title, cx, bottom - 34, w - 28, -11372);
               if (initiator) {
                  line(g, InteractionClient.text(cheek ? "hud.touch" : "hud.hold"), cx, bottom - 17, width - 16, -797215);
               }

               line(g, InteractionClient.text("hud.stop", PlayerKeys.OPEN.getTranslatedKeyMessage()), cx, bottom - 6, width - 16, -3426607);
               if (initiator) {
                  int cy = height / 2;
                  UiArt.hand(g, cx, cy, s.touching, s.mode == 1, zone);
                  if (zone >= 0) {
                     line(g, InteractionClient.text("zone." + zone), cx, cy + 12, width - 16, -11372);
                  }
               }
            } else if (Actions.scene(s.action) && s.joinTick() >= 0 && !s.joinComplete) {
               MutableComponent titlex = InteractionClient.text(
                  s.joinStarted ? "join_enter" : (s.waitingForJoin ? (initiator ? "join_hint" : "join_wait") : "join_intro")
               );
               int wx = Math.min(width - 16, mc.font.width(titlex) + 34);
               UiArt.panel(g, cx - wx / 2, bottom - 28, wx, 20);
               line(g, titlex, cx, bottom - 22, wx - 28, -11372);
               line(g, InteractionClient.text("hud.stop", PlayerKeys.OPEN.getTranslatedKeyMessage()), cx, bottom - 6, width - 16, -3426607);
            } else if (s.paused) {
               MutableComponent titlex = InteractionClient.text(initiator ? "pet_hint" : "pet_wait", PlayerKeys.OPEN.getTranslatedKeyMessage());
               MutableComponent hint = InteractionClient.text("pet_continue", PlayerKeys.OPEN.getTranslatedKeyMessage());
               int wx = Math.min(width - 16, Math.max(mc.font.width(titlex), mc.font.width(hint)) + 34);
               UiArt.panel(g, cx - wx / 2, bottom - 47, wx, 34);
               line(g, titlex, cx, bottom - 40, wx - 28, -11372);
               line(g, hint, cx, bottom - 28, wx - 28, -797215);
               g.fill(cx - 70, bottom - 17, cx + 70, bottom - 15, -11913905);
               g.fill(cx - 70, bottom - 17, cx - 70 + s.progress * 140 / 100, bottom - 15, -1071170);
               line(g, InteractionClient.text("escape"), cx, bottom - 6, width - 16, -3426607);
            } else {
               Component titlex = InteractionClient.action(s.action);
               int wx = Math.min(width - 16, mc.font.width(titlex) + 34);
               UiArt.panel(g, cx - wx / 2, bottom - 28, wx, 20);
               line(g, titlex, cx, bottom - 22, wx - 28, -11372);
               line(g, InteractionClient.text("hud.stop", PlayerKeys.OPEN.getTranslatedKeyMessage()), cx, bottom - 6, width - 16, -3426607);
            }
         } finally {
            g.pose().popPose();
         }
      }
   }

   private static int leftHeight(Minecraft mc) {
      return mc.gui instanceof net.minecraftforge.client.gui.overlay.ForgeGui g ? g.leftHeight : 39;
   }

   private static int rightHeight(Minecraft mc) {
      return mc.gui instanceof net.minecraftforge.client.gui.overlay.ForgeGui g ? g.rightHeight : 39;
   }

   static int bottom(int height, float scale, int leftHeight, int rightHeight) {
      return height - (int)Math.ceil((double)((float)Math.max(48, Math.max(leftHeight, rightHeight)) / scale));
   }

   private static void line(GuiGraphics g, Component text, int cx, int y, int available, int color) {
      Font font = Minecraft.getInstance().font;
      float fit = Math.min(1.0F, (float)available / (float)Math.max(1, font.width(text)));
      g.pose().pushPose();
      g.pose().translate((float)cx, (float)y, 0.0F);
      g.pose().scale(fit, fit, 1.0F);
      g.drawCenteredString(font, text, 0, 0, color);
      g.pose().popPose();
   }

   private InteractionHud() {
   }
}
