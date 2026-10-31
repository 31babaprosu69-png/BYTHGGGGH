package pro.webhm.playerinteractions.body;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BodyRuntime {
   public static final String MODID = "aot_player_interactions";
   public static final Logger LOG = LoggerFactory.getLogger("AoTBody");

   public static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("aot_player_interactions", path);
   }

   private BodyRuntime() {
   }
}
