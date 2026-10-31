package pro.webhm.playerinteractions;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("aot_player_interactions")
public final class PlayerInteractions {
   public static final String ID = "aot_player_interactions";

   public static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("aot_player_interactions", path);
   }

   public PlayerInteractions(IEventBus bus) {
      bus.addListener(Network::register);
      AnimationCatalog.prepare();
   }
}
