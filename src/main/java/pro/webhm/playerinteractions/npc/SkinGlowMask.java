package pro.webhm.playerinteractions.npc;

import java.util.Arrays;

public final class SkinGlowMask {
   public static boolean selected(String mask, int x, int y) {
      int i = y * 64 + x;
      return x >= 0 && x < 64 && y >= 0 && y < 64 && mask != null && i < mask.length() && mask.charAt(i) == '1';
   }

   public static String paint(String mask, int x, int y, boolean on) {
      if (x >= 0 && x < 64 && y >= 0 && y < 64) {
         char[] cells = new char[4096];
         Arrays.fill(cells, '0');

         for (int i = 0; i < 4096; i++) {
            if (mask != null && i < mask.length() && mask.charAt(i) == '1') {
               cells[i] = '1';
            }
         }

         cells[y * 64 + x] = (char)(on ? 49 : 48);
         return new String(cells);
      } else {
         return mask;
      }
   }

   public static String normalize(String mask) {
      return mask != null && mask.indexOf(49) >= 0 ? paint(mask, 0, 0, selected(mask, 0, 0)) : "";
   }
}
