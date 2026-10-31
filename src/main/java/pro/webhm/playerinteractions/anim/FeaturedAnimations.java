aot_player_interactions/scenes/package pro.webhm.playerinteractions.anim;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class FeaturedAnimations {
   private static List<AnimationBundle> load() {
      ArrayList<AnimationBundle> result = new ArrayList<>();

      for (String name : List.of("01", "02", "03")) {
         try (InputStream in = FeaturedAnimations.class.getResourceAsStream("/assets/aot_player_interactions/scenes/featured/" + name + ".json")) {
            if (in == null) {
               throw new IOException("Missing packaged animation " + name);
            }

            result.add(AnimationBundle.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), name));
         } catch (IOException var8) {
            throw new IllegalStateException(var8);
         }
      }

      return List.copyOf(result);
   }

   public static List<AnimationBundle> all() {
      return FeaturedAnimations.Loaded.ALL;
   }

   public static int index(int action) {
      for (int i = 0; i < all().size(); i++) {
         if (all().get(i).action() == action) {
            return i;
         }
      }

      return -1;
   }

   private FeaturedAnimations() {
   }

   private static final class Loaded {
      private static final List<AnimationBundle> ALL = FeaturedAnimations.load();
   }
}
