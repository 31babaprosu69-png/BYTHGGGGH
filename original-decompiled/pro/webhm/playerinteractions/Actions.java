package pro.webhm.playerinteractions;

import pro.webhm.playerinteractions.anim.Clip;

public final class Actions {
   public static boolean scene(int id) {
      return id >= 0 && id < 4 || id >= 7;
   }

   public static boolean together(int id) {
      return id == 5 || id == 6;
   }

   public static boolean supported(int id) {
      return id >= 0 && id <= 6 || Clip.hasCustom(id);
   }

   public static boolean needsBed(int id) {
      return scene(id) && (id < 4 || Clip.get(id, false).bedRelative);
   }

   private Actions() {
   }
}
