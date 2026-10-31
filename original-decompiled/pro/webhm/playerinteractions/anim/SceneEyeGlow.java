package pro.webhm.playerinteractions.anim;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class SceneEyeGlow {
   public int tick;
   public boolean enabled = true;
   public int fadeTicks = 20;

   public SceneEyeGlow copy() {
      SceneEyeGlow p = new SceneEyeGlow();
      p.tick = this.tick;
      p.enabled = this.enabled;
      p.fadeTicks = this.fadeTicks;
      return p;
   }

   public static List<SceneEyeGlow> normalize(List<SceneEyeGlow> source) {
      TreeMap<Integer, SceneEyeGlow> sorted = new TreeMap<>();
      if (source != null) {
         for (SceneEyeGlow p : source) {
            if (p != null) {
               SceneEyeGlow c = p.copy();
               c.tick = Math.max(0, Math.min(1000000, c.tick));
               c.fadeTicks = Math.max(1, Math.min(1200, c.fadeTicks));
               if (sorted.size() < 128 || sorted.containsKey(c.tick)) {
                  sorted.put(c.tick, c);
               }
            }
         }
      }

      return new ArrayList<>(sorted.values());
   }

   public static float sample(List<SceneEyeGlow> points, double time) {
      float from = 0.0F;
      float target = 0.0F;
      int start = 0;
      int fade = 1;

      for (SceneEyeGlow p : points) {
         if ((double)p.tick > time) {
            break;
         }

         from = blend(from, target, ((double)p.tick - (double)start) / (double)fade);
         start = p.tick;
         fade = p.fadeTicks;
         target = p.enabled ? 1.0F : 0.0F;
      }

      return blend(from, target, (time - (double)start) / (double)fade);
   }

   private static float blend(float from, float to, double progress) {
      float t = (float)Math.max(0.0, Math.min(1.0, progress));
      return from + (to - from) * t * t * (3.0F - 2.0F * t);
   }

   public static void inherit(NpcAnimation host, NpcAnimation guest) {
      if (host.sharedEyeGlow) {
         guest.eyeGlow = normalize(host.eyeGlow);

         for (SceneEyeGlow p : guest.eyeGlow) {
            p.tick = Math.max(0, p.tick - host.entryTicks);
         }
      }

      guest.sharedEyeGlow = false;
   }
}
