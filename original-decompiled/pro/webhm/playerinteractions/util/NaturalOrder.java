package pro.webhm.playerinteractions.util;

import java.util.Comparator;

public final class NaturalOrder {
   public static final Comparator<String> INSTANCE = NaturalOrder::compare;

   public static int compare(String a, String b) {
      if (a == null) {
         return b == null ? 0 : -1;
      } else if (b == null) {
         return 1;
      } else {
         int i = 0;
         int j = 0;

         while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (digit(ca) && digit(cb)) {
               int si = i;
               int sj = j;

               while (si < a.length() && a.charAt(si) == '0') {
                  si++;
               }

               while (sj < b.length() && b.charAt(sj) == '0') {
                  sj++;
               }

               int ei = si;

               while (ei < a.length() && digit(a.charAt(ei))) {
                  ei++;
               }

               int ej = sj;

               while (ej < b.length() && digit(b.charAt(ej))) {
                  ej++;
               }

               int lenA = ei - si;
               int lenB = ej - sj;
               if (lenA != lenB) {
                  return Integer.compare(lenA, lenB);
               }

               for (int k = 0; k < lenA; k++) {
                  int d = a.charAt(si + k) - b.charAt(sj + k);
                  if (d != 0) {
                     return d;
                  }
               }

               i = ei;
               j = ej;
            } else {
               char la = Character.toLowerCase(ca);
               char lb = Character.toLowerCase(cb);
               if (la != lb) {
                  return la - lb;
               }

               i++;
               j++;
            }
         }

         int rest = a.length() - i - (b.length() - j);
         return rest != 0 ? rest : a.compareTo(b);
      }
   }

   private static boolean digit(char c) {
      return c >= '0' && c <= '9';
   }

   private NaturalOrder() {
   }
}
