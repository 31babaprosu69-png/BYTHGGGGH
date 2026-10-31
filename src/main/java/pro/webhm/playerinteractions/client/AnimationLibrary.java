package pro.webhm.playerinteractions.client;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.network.chat.Component;
import pro.webhm.playerinteractions.AnimationCatalog;
import pro.webhm.playerinteractions.Network;
import pro.webhm.playerinteractions.anim.AnimationBundle;
import pro.webhm.playerinteractions.anim.CatalogTransfer;
import pro.webhm.playerinteractions.anim.Clip;
import pro.webhm.playerinteractions.anim.FeaturedAnimations;

public final class AnimationLibrary {
   private static List<AnimationBundle> entries = List.of();
   private static CatalogTransfer transfer;
   private static AnimationCatalog.Header header;
   private static boolean ready;
   private static boolean failed;
   private static int revision;
   private static int skipped;
   private static boolean editable;

   public static List<AnimationBundle> entries() {
      return entries;
   }

   public static int revision() {
      return revision;
   }

   public static int skipped() {
      return skipped;
   }

   public static boolean ready() {
      return ready;
   }

   public static boolean failed() {
      return failed;
   }

   public static boolean editable() {
      return editable;
   }

   public static String name(int id) {
      return entries.stream().filter(e -> e.action() == id).map(AnimationBundle::name).findFirst().orElse("Animation");
   }

   public static Component label(int id) {
      int index = FeaturedAnimations.index(id);
      return index >= 0 ? InteractionClient.text("featured." + index) : Component.literal(name(id));
   }

   public static void begin(String json) {
      try {
         header = (AnimationCatalog.Header)Clip.JSON.fromJson(json, AnimationCatalog.Header.class);
         transfer = new CatalogTransfer(header.generation(), header.count());
         failed = false;
      } catch (RuntimeException var2) {
         fail(var2);
      }
   }

   public static void receive(Network.CatalogData packet) {
      if (transfer != null) {
         try {
            transfer.add(packet.generation(), packet.action(), packet.json());
         } catch (RuntimeException var2) {
            fail(var2);
         }
      }
   }

   public static void finish(String json) {
      if (transfer != null) {
         try {
            List<AnimationBundle> next = transfer.finish(Integer.parseInt(json));
            Clip.installClientCustom(next);
            entries = next;
            editable = header.editable();
            skipped = Math.max(0, header.skipped());
            ready = true;
            transfer = null;
            revision++;
            InteractionClient.catalogReady();
         } catch (RuntimeException var2) {
            fail(var2);
         }
      }
   }

   private static void fail(RuntimeException ex) {
      transfer = null;
      failed = true;
      revision++;
      LogUtils.getLogger().warn("Cannot receive paired animation library", ex);
   }

   public static void clear() {
      entries = List.of();
      Clip.installClientCustom(entries);
      transfer = null;
      header = null;
      editable = false;
      failed = false;
      ready = false;
      skipped = 0;
      revision++;
   }

   private AnimationLibrary() {
   }
}
