package pro.webhm.playerinteractions.adornments;

import java.util.ArrayList;

public final class AdornmentMesh {
   public final float[] vertices;

   public AdornmentMesh(AdornmentProject.Link l) {
      this(l, false);
   }

   public AdornmentMesh(AdornmentProject.Link l, boolean linked) {
      ArrayList<Float> out = new ArrayList<>();
      float w = l.width * 0.5F;
      float d = l.depth * 0.5F;
      float h = l.length;
      if (l.shape == 1) {
         float t = Math.min(l.wire, Math.min(l.width * 0.4F, h * 0.3F));
         float overlap = linked ? 0.0F : Math.min(h * 0.2F, t + d * 0.55F);
         box(out, -w, -overlap, -d, w, t - overlap, d, l);
         box(out, -w, h + overlap - t, -d, w, h + overlap, d, l);
         box(out, -w, t - overlap, -d, -w + t, h + overlap - t, d, l);
         box(out, w - t, t - overlap, -d, w, h + overlap - t, d, l);
      } else if (l.shape == 2) {
         for (int i = 0; i < 5; i++) {
            float k = 1.0F - (float)i / 6.0F;
            box(out, -w * k, (float)i * h / 5.0F, -d * k, w * k, (float)(i + 1) * h / 5.0F, d * k, l);
         }
      } else {
         box(out, -w, 0.0F, -d, w, h, d, l);
      }

      this.vertices = new float[out.size()];

      for (int i = 0; i < this.vertices.length; i++) {
         this.vertices[i] = out.get(i);
      }
   }

   private static void box(ArrayList<Float> out, float x0, float y0, float z0, float x1, float y1, float z1, AdornmentProject.Link l) {
      float[][] p = new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
      int[][] faces = new int[][]{{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {0, 1, 5, 4}, {3, 7, 6, 2}};
      float[][] normals = new float[][]{
         {0.0F, 0.0F, -1.0F}, {0.0F, 0.0F, 1.0F}, {-1.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F}, {0.0F, -1.0F, 0.0F}, {0.0F, 1.0F, 0.0F}
      };

      for (int f = 0; f < 6; f++) {
         for (int v = 0; v < 4; v++) {
            for (float n : p[faces[f][v]]) {
               out.add(n);
            }

            for (float n : normals[f]) {
               out.add(n);
            }

            out.add(l.solid ? 0.0F : ((float)l.u + (v != 1 && v != 2 ? 0.0F : Math.min(l.width, (float)(64 - l.u)))) / 64.0F);
            out.add(l.solid ? 0.0F : ((float)l.v + (v >= 2 ? Math.min(l.length, (float)(64 - l.v)) : 0.0F)) / 64.0F);
         }
      }
   }
}
