package pro.webhm.playerinteractions.adornments;

import java.util.List;

public final class AdornmentGripKey {
   public int tick;
   public String rig = "";
   public String link = "";
   public boolean held = true;
   public int target;
   public float x;
   public float y;
   public float z;

   public boolean valid(int duration) {
      return this.tick >= 0
         && this.tick <= duration
         && this.rig != null
         && this.link != null
         && this.rig.matches("[a-zA-Z0-9_-]{1,40}")
         && this.link.matches("[a-zA-Z0-9_-]{1,40}")
         && this.target >= 0
         && this.target <= 4
         && Float.isFinite(this.x)
         && Float.isFinite(this.y)
         && Float.isFinite(this.z)
         && Math.abs(this.x) <= 128.0F
         && Math.abs(this.y) <= 128.0F
         && Math.abs(this.z) <= 128.0F;
   }

   public static AdornmentGripKey sample(List<AdornmentGripKey> keys, String rig, double tick) {
      AdornmentGripKey last = null;

      for (AdornmentGripKey key : keys) {
         if (key.rig.equals(rig) && (double)key.tick <= tick && (last == null || key.tick >= last.tick)) {
            last = key;
         }
      }

      return last != null && last.held ? last : null;
   }
}
