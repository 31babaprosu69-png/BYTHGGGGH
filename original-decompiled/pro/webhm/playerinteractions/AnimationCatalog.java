package pro.webhm.playerinteractions;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import pro.webhm.playerinteractions.anim.AnimationBundle;
import pro.webhm.playerinteractions.anim.AnimationFiles;
import pro.webhm.playerinteractions.anim.Clip;
import pro.webhm.playerinteractions.anim.FeaturedAnimations;

@EventBusSubscriber(
   modid = "aot_player_interactions"
)
public final class AnimationCatalog {
   private static final Logger LOG = LogUtils.getLogger();
   private static List<AnimationBundle> entries = FeaturedAnimations.all();
   private static final Map<UUID, Long> REQUESTS = new HashMap<>();
   private static int generation;
   private static int skipped;

   public static Path directory() {
      return FMLPaths.MODSDIR.get().resolve("aot_player_interactions").resolve("animations");
   }

   public static void prepare() {
      try {
         CatalogInstructions.prepare(directory());
      } catch (IOException var1) {
         LOG.warn("Cannot create custom animation folder", var1);
      }
   }

   private static boolean reload() {
      try {
         AnimationFiles.Result result = AnimationFiles.load(directory(), FeaturedAnimations.all());
         entries = result.animations();
         skipped = result.errors().size();
         generation++;
         Clip.installCustom(entries);

         for (String error : result.errors()) {
            LOG.warn("Custom animation skipped: {}", error);
         }

         LOG.info("Loaded {} custom paired animations from {}", entries.size(), directory());
         return true;
      } catch (IOException var3) {
         LOG.warn("Animation catalogue unchanged: cannot read folder", var3);
         return false;
      }
   }

   public static boolean allowed(int action) {
      return action >= 4 && action <= 6 || entries.stream().anyMatch(e -> e.action() == action);
   }

   public static boolean editable(ServerPlayer player) {
      GameProfile owner = player.server.getSingleplayerProfile();
      return player.hasPermissions(2) || player.server.isSingleplayer() && owner != null && owner.getId().equals(player.getUUID());
   }

   public static void send(ServerPlayer player) {
      PacketDistributor.sendToPlayer(
         player,
         new Network.Update("catalog_begin", Clip.JSON.toJson(new AnimationCatalog.Header(generation, entries.size(), skipped, editable(player)))),
         new CustomPacketPayload[0]
      );

      for (AnimationBundle entry : entries) {
         PacketDistributor.sendToPlayer(
            player, new Network.CatalogData(generation, entry.action(), entry.json().getBytes(StandardCharsets.UTF_8)), new CustomPacketPayload[0]
         );
      }

      PacketDistributor.sendToPlayer(player, new Network.Update("catalog_end", Integer.toString(generation)), new CustomPacketPayload[0]);
   }

   public static void request(ServerPlayer player, boolean reload) {
      long now = player.server.overworld().getGameTime();
      if (now - REQUESTS.getOrDefault(player.getUUID(), -100L) >= 40L) {
         REQUESTS.put(player.getUUID(), now);
         if (reload && editable(player)) {
            if (InteractionServer.busy()) {
               player.displayClientMessage(Component.translatable("aot_players.catalog.busy"), true);
               send(player);
               return;
            }

            if (reload()) {
               for (ServerPlayer viewer : player.server.getPlayerList().getPlayers()) {
                  send(viewer);
               }

               return;
            }

            player.displayClientMessage(Component.translatable("aot_players.catalog.failed"), true);
         }

         send(player);
      }
   }

   @SubscribeEvent
   public static void starting(ServerAboutToStartEvent event) {
      entries = FeaturedAnimations.all();
      Clip.installCustom(entries);
      prepare();
      reload();
   }

   @SubscribeEvent
   public static void login(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         send(p);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent event) {
      REQUESTS.remove(event.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent event) {
      entries = List.of();
      Clip.installCustom(entries);
      REQUESTS.clear();
   }

   private AnimationCatalog() {
   }

   public static record Header(int generation, int count, int skipped, boolean editable) {
   }
}
