package pro.webhm.playerinteractions.client;

public final class UiSlices {
   public static final int PANEL_WIDTH = 1402;
   public static final int PANEL_HEIGHT = 1122;
   public static final int BAR_WIDTH = 1983;
   public static final int BAR_HEIGHT = 793;

   public static void panel(int x, int y, int w, int h, UiSlices.Blitter out) {
      if (w > 0 && h > 0) {
         int edge = Math.min(32, Math.min(w, h) / 2);
         int sourceEdge = 224;
         int[] sx = new int[]{0, sourceEdge, 1402 - sourceEdge};
         int[] sw = new int[]{sourceEdge, 1402 - 2 * sourceEdge, sourceEdge};
         int[] sy = new int[]{0, sourceEdge, 1122 - sourceEdge};
         int[] sh = new int[]{sourceEdge, 1122 - 2 * sourceEdge, sourceEdge};
         int[] dx = new int[]{x, x + edge, x + w - edge};
         int[] dw = new int[]{edge, w - 2 * edge, edge};
         int[] dy = new int[]{y, y + edge, y + h - edge};
         int[] dh = new int[]{edge, h - 2 * edge, edge};

         for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
               if (dw[col] > 0 && dh[row] > 0) {
                  out.draw(dx[col], dy[row], dw[col], dh[row], sx[col], sy[row], sw[col], sh[row]);
               }
            }
         }
      }
   }

   public static void bar(int x, int y, int w, int h, UiSlices.Blitter out) {
      if (w > 0 && h > 0) {
         int cap = Math.min(w / 2, Math.max(1, Math.round((float)h * 150.0F / 250.0F)));
         if (cap > 0) {
            out.draw(x, y, cap, h, 110, 245, 150, 250);
         }

         if (w > 2 * cap) {
            out.draw(x + cap, y, w - 2 * cap, h, 260, 245, 1465, 250);
         }

         if (cap > 0) {
            out.draw(x + w - cap, y, cap, h, 1725, 245, 150, 250);
         }
      }
   }

   private UiSlices() {
   }

   @FunctionalInterface
   public interface Blitter {
      void draw(int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8);
   }
}
