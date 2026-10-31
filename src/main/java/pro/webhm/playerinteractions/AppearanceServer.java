package pro.webhm.playerinteractions;

import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.Clone;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.StartTracking;
import pro.webhm.playerinteractions.anim.Clip;

@Mod.EventBusSubscriber(
   modid = "aot_player_interactions"
)
public final class AppearanceServer {
   private static final String KEY = "aot_player_interactions_appearance";
   private static final Map<UUID, Long> LAST = new HashMap<>();

   public static Appearance.Settings get(ServerPlayer player) {
      String json = player.getPersistentData().getString("aot_player_interactions_appearance");

      try {
         return Appearance.decode(json);
      } catch (RuntimeException var3) {
         return Appearance.decode("");
      }
   }

   public static void set(ServerPlayer player, String json) {
      long now = player.server.overworld().getGameTime();
      if (now - LAST.getOrDefault(player.getUUID(), -100L) >= 2L) {
         LAST.put(player.getUUID(), now);

         try {
            Appearance.Settings b = Appearance.decode(json);
            player.getPersistentData().putString("aot_player_interactions_appearance", Clip.JSON.toJson(b));
            Network.toTrackingAndSelf(player, packet(player));
         } catch (RuntimeException var5) {
         }
      }
   }

   private static Network.Update packet(ServerPlayer player) {
      Appearance.Settings s = get(player);
      return new Network.Update("appearance", Clip.JSON.toJson(new Appearance.Snapshot(player.getUUID(), s.breast(), s.arms())));
   }

   @SubscribeEvent
   public static void login(PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer player) {
         for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            Network.toPlayer(player, packet(other));
         }

         Network.toTrackingAndSelf(player, packet(player));
      }
   }

   @SubscribeEvent
   public static void track(StartTracking e) {
      if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer target) {
         Network.toPlayer(viewer, packet(target));
      }
   }

   @SubscribeEvent
   public static void clonePlayer(Clone e) {
      if (e.getOriginal().getPersistentData().contains("aot_player_interactions_appearance")) {
         e.getEntity()
            .getPersistentData()
            .putString("aot_player_interactions_appearance", e.getOriginal().getPersistentData().getString("aot_player_interactions_appearance"));
      }
   }

   @SubscribeEvent
   public static void respawn(PlayerRespawnEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         Network.toTrackingAndSelf(p, packet(p));
      }
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent e) {
      LAST.remove(e.getEntity().getUUID());
   }

   private AppearanceServer() {
   }
}
