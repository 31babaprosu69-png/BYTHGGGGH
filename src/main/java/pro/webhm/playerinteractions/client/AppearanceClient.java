package pro.webhm.playerinteractions.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import pro.webhm.playerinteractions.Appearance;
import pro.webhm.playerinteractions.Network;
import pro.webhm.playerinteractions.anim.Clip;
import pro.webhm.playerinteractions.npc.BreastData;

public final class AppearanceClient {
   private static final Map<UUID, BreastData> VALUES = new HashMap<>();
   private static final Map<UUID, Integer> ARMS = new HashMap<>();
   public static Integer previewArms;
   public static BreastData preview;

   public static int arms(UUID id) {
      LocalPlayer p = Minecraft.getInstance().player;
      return previewArms != null && p != null && p.getUUID().equals(id) ? previewArms : ARMS.getOrDefault(id, 0);
   }

   public static BreastData get(UUID id) {
      LocalPlayer player = Minecraft.getInstance().player;
      return preview != null && player != null && player.getUUID().equals(id) ? preview : VALUES.computeIfAbsent(id, key -> Appearance.defaults());
   }

   public static void receive(String json) {
      Appearance.Snapshot s = (Appearance.Snapshot)Clip.JSON.fromJson(json, Appearance.Snapshot.class);
      VALUES.put(s.player(), Appearance.sanitize(s.breast()));
      ARMS.put(s.player(), Appearance.arms(s.arms()));
   }

   public static void save(BreastData b, int arms) {
      PacketDistributor.sendToServer(
         new Network.AppearanceChange(Clip.JSON.toJson(new Appearance.Settings(Appearance.sanitize(b), Appearance.arms(arms)))), new CustomPacketPayload[0]
      );
   }

   public static void clear() {
      VALUES.clear();
      ARMS.clear();
      preview = null;
      previewArms = null;
   }

   private AppearanceClient() {
   }
}
