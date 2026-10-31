package pro.webhm.playerinteractions.anim;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class SceneScaleKey {
   public int tick;
   public boolean natural;
   public float scale = 1.5F;

   public SceneScaleKey copy() {
      SceneScaleKey k = new SceneScaleKey();
      k.tick = this.tick;
      k.natural = this.natural;
      k.scale = this.scale;
      return k;
   }

   public static float clamp(float value) {
      return Float.isFinite(value) ? Math.max(0.1F, Math.min(8.0F, value)) : 1.0F;
   }

   public float value(float base) {
      return this.natural ? clamp(base) : clamp(this.scale);
   }

   public static List<SceneScaleKey> normalize(List<SceneScaleKey> keys) {
      TreeMap<Integer, SceneScaleKey> sorted = new TreeMap<>();
      if (keys != null) {
         for (SceneScaleKey key : keys) {
            if (key != null) {
               SceneScaleKey k = key.copy();
               k.tick = Math.max(0, Math.min(1000000, k.tick));
               k.scale = clamp(k.scale);
               if (sorted.size() < 128 || sorted.containsKey(k.tick)) {
                  sorted.put(k.tick, k);
               }
            }
         }
      }

      return new ArrayList<>(sorted.values());
   }

   public static float sample(List<SceneScaleKey> keys, float base, double time) {
      base = clamp(base);
      if (keys != null && !keys.isEmpty() && Double.isFinite(time) && !(time < (double)keys.get(0).tick)) {
         SceneScaleKey left = keys.get(0);

         for (int i = 1; i < keys.size(); i++) {
            SceneScaleKey right = keys.get(i);
            if (time < (double)right.tick) {
               double t = Math.max(0.0, Math.min(1.0, (time - (double)left.tick) / ((double)right.tick - (double)left.tick)));
               t = t * t * (3.0 - 2.0 * t);
               return (float)((double)left.value(base) + (double)(right.value(base) - left.value(base)) * t);
            }

            left = right;
         }

         return left.value(base);
      } else {
         return base;
      }
   }
}
