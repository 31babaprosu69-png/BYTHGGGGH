package pro.webhm.playerinteractions.anim;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

public final class AnimationMenu {
   public static final int ROWS = 10;

   public static List<Integer> actions(List<AnimationBundle> entries) {
      HashSet<Integer> available = new HashSet<>();

      for (AnimationBundle entry : entries) {
         available.add(entry.action());
      }

      LinkedHashSet<Integer> result = new LinkedHashSet<>();

      for (AnimationBundle entry : FeaturedAnimations.all()) {
         if (available.contains(entry.action())) {
            result.add(Integer.valueOf(entry.action()));
         }
      }

      for (AnimationBundle entryx : entries) {
         result.add(Integer.valueOf(entryx.action()));
      }

      return List.copyOf(result);
   }

   public static int clamp(int scroll, int count) {
      return Math.max(0, Math.min(scroll, Math.max(0, count - 10)));
   }

   public static int fromTrack(double fraction, int count) {
      return clamp((int)Math.round(Math.max(0.0, Math.min(1.0, fraction)) * (double)Math.max(0, count - 10)), count);
   }

   private AnimationMenu() {
   }
}
