package pro.webhm.playerinteractions.facial;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.TreeSet;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

public final class FacialMesh {
   private static float radius(FacialData d, FacialPose p) {
      return d.width * p.width() * (1.0F + 0.12F * Math.max(0.0F, p.smile())) / 2.0F;
   }

   private static float[] columns(FacialData d, FacialPose p) {
      TreeSet<Float> values = new TreeSet<>();

      for (int i = 0; i <= 32; i++) {
         values.add(-4.0F + 8.0F * (float)i / 32.0F);
      }

      float radius = radius(d, p);

      for (float t : new float[]{
         -2.0F,
         -1.0F,
         -0.9375F,
         -0.875F,
         -0.75F,
         -0.625F,
         -0.5F,
         -0.375F,
         -0.25F,
         -0.125F,
         0.0F,
         0.125F,
         0.25F,
         0.375F,
         0.5F,
         0.625F,
         0.75F,
         0.875F,
         0.9375F,
         1.0F,
         2.0F
      }) {
         values.add(Math.max(-4.0F, Math.min(4.0F, d.x + radius * t)));
      }

      ArrayList<Float> distinct = new ArrayList<>();

      for (float x : values) {
         if (distinct.isEmpty() || x - distinct.getLast() > 1.0E-4F) {
            distinct.add(x);
         }
      }

      float[] result = new float[distinct.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = distinct.get(i);
      }

      return result;
   }

   public static float aperture(float x, FacialData d, FacialPose p) {
      float t = Math.abs((x - d.x) / radius(d, p));
      float q = Math.max(0.0F, 1.0F - t * t);
      float round = FacialExpression.ease((double)((0.96F - p.width()) / 0.38F));
      return (float)Math.pow((double)q, (double)(0.78F - 0.23F * round));
   }

   public static float chin(float x, float z, FacialData d, FacialPose p) {
      float support = Math.min(3.5F, d.width * 0.5F + 0.9F);
      float flat = d.width * 0.3F;
      float t = Math.max(0.0F, Math.min(1.0F, (support - Math.abs(x - d.x)) / (support - flat)));
      float edge = Math.max(0.0F, Math.min(1.0F, (4.0F - Math.abs(x)) / 0.65F));
      return d.jawDrop * p.jaw() * 0.65F * Math.max(0.0F, Math.min(1.0F, (4.0F - z) / 8.0F)) * t * t * (3.0F - 2.0F * t) * edge;
   }

   private static float corner(float x, FacialData d, FacialPose p) {
      float t = Math.abs((x - d.x) / radius(d, p));
      float curve = Math.max(0.0F, 1.0F - Math.abs(t - 1.0F));
      curve = curve * curve * (3.0F - 2.0F * curve);
      float side = Math.signum(x - d.x) * (p.acting().asymmetry() + p.cornerAsymmetry());
      float released = FacialExpression.ease((double)(p.open() / 0.3F)) * (t < 1.0F ? 1.0F - curve : 0.0F);
      return -0.23F * ((p.smile() + p.cornerLift()) * (curve + released) + side * curve) * Math.min(1.0F, Math.max(0.0F, (4.0F - Math.abs(x)) / 0.65F));
   }

   public static float upper(float x, FacialData d, FacialPose p) {
      float round = FacialExpression.ease((double)((0.96F - p.width()) / 0.38F));
      float t = Math.abs((x - d.x) / radius(d, p));
      float support = FacialExpression.ease((double)(2.0F - t)) * Math.min(1.0F, Math.max(0.0F, (4.0F - Math.abs(x)) / 0.65F));
      float opening = d.height * p.open();
      return Math.min(
         -0.05F,
         d.y
            - 8.0F
            + corner(x, d, p) * (1.0F - 0.35F * p.open())
            + opening * (0.3F * round * support - (0.14F + 0.36F * round) * aperture(x, d, p))
            + opening * p.contact().influence((x - d.x) / radius(d, p)) * (0.12F * p.contact().compression() + 0.18F * p.contact().vertical())
      );
   }

   public static float lower(float x, FacialData d, FacialPose p) {
      float top = upper(x, d, p);
      float gap = Math.min(d.height * p.open() * (1.0F - 0.18F * Math.max(0.0F, p.smile())), (chin(x, -4.0F, d, p) - top) * 0.85F);
      float local = p.contact().influence((x - d.x) / radius(d, p));
      return top + gap * aperture(x, d, p) * (1.0F - 0.24F * local * p.contact().compression());
   }

   public static float lipZ(float x, FacialData d, FacialPose p) {
      return -4.0F;
   }

   public static Vector3f contactPoint(FacialData d, FacialPose p, int side) {
      float u = p.contact().weight() > 0.001F ? p.contact().x() : (float)side * 0.58F;
      return lipPoint(d.x + radius(d, p) * u, 0.35F, false, d, p);
   }

   private static float jawStart(FacialData d) {
      return Math.min(-0.04F, d.y - 8.0F + 0.4F);
   }

   public static float skinY(float x, float renderedY, FacialData d, FacialPose p) {
      float start = jawStart(d);
      float drop = chin(x, -4.0F, d, p);
      return renderedY <= start ? renderedY : (renderedY + drop * start / -start) / (1.0F + drop / -start);
   }

   public static Vector3f deform(float x, float y, float z, FacialData d, FacialPose p) {
      float start = jawStart(d);
      float t = Math.max(0.0F, Math.min(1.0F, (y - start) / -start));
      float jaw = chin(x, z, d, p) * t;
      return new Vector3f(x, y + jaw, z - jaw * 0.08F);
   }

   public static void skin(PoseStack s, VertexConsumer out, int light, int overlay, int color, FacialData d, FacialPose p, boolean outer) {
      float inflate = outer ? 0.5F : 0.0F;
      float scale = 1.0F + inflate / 4.0F;
      float uv = outer ? 32.0F : 0.0F;
      float seam = d.y - 8.0F;
      float[] columns = columns(d, p);

      for (boolean below : new boolean[]{false, true}) {
         for (int col = 0; col < columns.length - 1; col++) {
            float x0 = columns[col];
            float x1 = columns[col + 1];
            patch(s, out, light, overlay, color, 1, 12, (u, v) -> {
               float x = x0 + (x1 - x0) * u;
               float edge = below ? lower(x, d, p) : upper(x, d, p);
               float py = below ? edge + (chin(x, -4.0F, d, p) - edge) * v : -8.0F + (edge + 8.0F) * v;
               float sourceY = skinY(x, py, d, p);
               float z = deform(x, sourceY, -4.0F, d, p).z;
               return new FacialMesh.V(new Vector3f(x * scale, (py + 4.0F) * scale - 4.0F, z * scale), uv + 12.0F + x, 16.0F + sourceY);
            }, false);
         }
      }

      for (int face = 0; face < 5; face++) {
         int f = face;
         patch(s, out, light, overlay, color, f == 4 ? 16 : 1, f == 4 ? 16 : 1, (u, v) -> {
            float x;
            float y;
            float z;
            float tu;
            float tv;
            if (f == 0) {
               x = 4.0F - 8.0F * u;
               y = -8.0F + 8.0F * v;
               z = 4.0F;
               tu = uv + 24.0F + 8.0F * u;
               tv = 8.0F + 8.0F * v;
            } else if (f == 1) {
               x = -4.0F;
               y = -8.0F + 8.0F * v;
               z = 4.0F - 8.0F * u;
               tu = uv + 8.0F * u;
               tv = 8.0F + 8.0F * v;
            } else if (f == 2) {
               x = 4.0F;
               y = -8.0F + 8.0F * v;
               z = -4.0F + 8.0F * u;
               tu = uv + 16.0F + 8.0F * u;
               tv = 8.0F + 8.0F * v;
            } else if (f == 3) {
               x = -4.0F + 8.0F * u;
               y = -8.0F;
               z = 4.0F - 8.0F * v;
               tu = uv + 8.0F + 8.0F * u;
               tv = 8.0F * v;
            } else {
               x = -4.0F + 8.0F * u;
               y = 0.0F;
               z = -4.0F + 8.0F * v;
               tu = uv + 16.0F + 8.0F * u;
               tv = 8.0F - 8.0F * v;
            }

            Vector3f point = deform(x, y, z, d, p);
            point.x *= scale;
            point.y = (point.y + 4.0F) * scale - 4.0F;
            point.z *= scale;
            return new FacialMesh.V(point, tu, tv);
         }, false);
      }
   }

   public static void features(PoseStack s, VertexConsumer out, int light, FacialData d, FacialPose p) {
      float left = d.x - radius(d, p);
      float right = d.x + radius(d, p);
      float[] columns = columns(d, p);

      for (int i = 0; i < columns.length - 1; i++) {
         float a = columns[i];
         float b = columns[i + 1];
         if (!(a < left - 1.0E-5F) && !(b > right + 1.0E-5F)) {
            float ta = upper(a, d, p);
            float tb = upper(b, d, p);
            float ba = lower(a, d, p);
            float bb = lower(b, d, p);
            float za = lipZ(a, d, p) - 0.001F;
            float zb = lipZ(b, d, p) - 0.001F;
            float back = -4.0F + d.depth;
            float lowerZa = deform(a, skinY(a, ba, d, p), -4.0F, d, p).z - 0.001F;
            float lowerZb = deform(b, skinY(b, bb, d, p), -4.0F, d, p).z - 0.001F;
            float line = 0.042F * (1.0F - Math.min(1.0F, p.open() * 3.0F));
            if (line > 0.001F) {
               float ha = line * aperture(a, d, p);
               float hb = line * aperture(b, d, p);
               float crease = 0.006F + d.lipProjection * 0.2F * (1.0F - Math.min(1.0F, p.open() * 3.0F));
               quad(
                  s,
                  out,
                  light,
                  d.innerColor,
                  new Vector3f(a, ta - ha / 2.0F, za - crease),
                  new Vector3f(a, ta + ha / 2.0F, za - crease),
                  new Vector3f(b, tb + hb / 2.0F, zb - crease),
                  new Vector3f(b, tb - hb / 2.0F, zb - crease)
               );
            }

            quad(
               s,
               out,
               light,
               shade(d.innerColor, 0.55F),
               new Vector3f(a, ta, back),
               new Vector3f(a, ba, back),
               new Vector3f(b, bb, back),
               new Vector3f(b, tb, back)
            );
            quad(
               s, out, light, shade(d.innerColor, 0.8F), new Vector3f(a, ta, za), new Vector3f(a, ta, back), new Vector3f(b, tb, back), new Vector3f(b, tb, zb)
            );
            quad(s, out, light, d.innerColor, new Vector3f(a, ba, back), new Vector3f(a, ba, lowerZa), new Vector3f(b, bb, lowerZb), new Vector3f(b, bb, back));
            float rim = Math.min(0.08F, d.lipVolume * 0.35F) * p.open();
            if (rim > 0.0F) {
               float ha = Math.min(rim, (ba - ta) * 0.12F);
               float hb = Math.min(rim, (bb - tb) * 0.12F);
               quad(
                  s,
                  out,
                  light,
                  shade(d.tongueColor, 0.7F),
                  new Vector3f(a, ba - ha, lowerZa + 0.03F),
                  new Vector3f(a, ba, lowerZa + 0.03F),
                  new Vector3f(b, bb, lowerZb + 0.03F),
                  new Vector3f(b, bb - hb, lowerZb + 0.03F)
               );
            }
         }
      }

      lips(s, out, light, d, p, columns);
      innerTongue(s, out, light, d, p);
      teeth(s, out, light, d, p);
   }

   private static void innerTongue(PoseStack s, VertexConsumer out, int light, FacialData d, FacialPose p) {
      if (d.tongue && !(p.open() <= 0.0F)) {
         float bottom = lower(d.x, d, p) + 0.035F;
         float height = Math.min(0.36F, (bottom - upper(d.x, d, p)) * 0.38F);
         float width = Math.min(0.85F, d.width * 0.4F);
         float z = -4.0F + d.depth * 0.52F;

         for (int i = 0; i < 16; i++) {
            float u = -1.0F + (float)i / 8.0F;
            float v = u + 0.125F;
            float a = d.x + width * u;
            float b = d.x + width * v;
            quad(
               s,
               out,
               light,
               shade(d.tongueColor, 0.86F),
               new Vector3f(a, bottom - height * (1.0F - u * u), z),
               new Vector3f(a, bottom, z),
               new Vector3f(b, bottom, z),
               new Vector3f(b, bottom - height * (1.0F - v * v), z)
            );
         }
      }
   }

   public static void teeth(PoseStack s, VertexConsumer out, int light, FacialData d, FacialPose p) {
      if (d.teeth && !(p.open() <= 0.0F)) {
         float radius = d.width * 0.64F;
         float top = d.y - 8.0F - Math.min(0.32F, d.height * 0.25F);
         float bottom = d.y - 8.0F - 0.012F;

         for (int i = 0; i < 20; i++) {
            float u = -1.0F + (float)i * 0.1F;
            float v = u + 0.1F;
            float a = d.x + radius * u;
            float b = d.x + radius * v;
            float za = -4.0F + d.depth * (0.13F + 0.1F * u * u);
            float zb = -4.0F + d.depth * (0.13F + 0.1F * v * v);
            quad(s, out, light, d.teethColor, new Vector3f(a, top, za), new Vector3f(a, bottom, za), new Vector3f(b, bottom, zb), new Vector3f(b, top, zb));
         }
      }
   }

   static void lips(PoseStack s, VertexConsumer out, int light, FacialData d, FacialPose p) {
      lips(s, out, light, d, p, columns(d, p));
   }

   private static void lips(PoseStack s, VertexConsumer out, int light, FacialData d, FacialPose p, float[] columns) {
      float r = radius(d, p);
      int first = 0;
      int last = columns.length - 1;

      while (first < last && columns[first] < d.x - r - 1.0E-5F) {
         first++;
      }

      while (last > first && columns[last] > d.x + r + 1.0E-5F) {
         last--;
      }

      int count = last - first + 1;
      int[] ix = new int[4];
      int[] iy = new int[4];

      for (boolean top : new boolean[]{true, false}) {
         if (!((top ? d.upperLip : d.lowerLip) <= 0.0F)) {
            Vector3f[][] points = new Vector3f[count][7];
            Vector3f[][] normals = new Vector3f[count][7];
            int[][] colors = new int[count][7];

            for (int i = 0; i < count; i++) {
               for (int j = 0; j <= 6; j++) {
                  float x = columns[first + i];
                  float v = (float)j / 6.0F;
                  float taper = aperture(x, d, p);
                  points[i][j] = lipPoint(x, v, top, d, p);
                  float tone = 1.0F - taper * (top ? 0.09F : 0.04F) * (1.0F - (float)Math.sin(Math.PI * (double)v));
                  colors[i][j] = shade(d.lipColor, tone);
               }
            }

            for (int i = 0; i < count; i++) {
               for (int j = 0; j <= 6; j++) {
                  Vector3f across = new Vector3f(points[Math.min(count - 1, i + 1)][j]).sub(points[Math.max(0, i - 1)][j]);
                  Vector3f along = new Vector3f(points[i][Math.min(6, j + 1)]).sub(points[i][Math.max(0, j - 1)]);
                  Vector3f n = along.cross(across);
                  if (top) {
                     n.negate();
                  }

                  if (n.lengthSquared() < 1.0E-10F) {
                     n.set(0.0F, 0.0F, -1.0F);
                  } else {
                     n.normalize();
                  }

                  normals[i][j] = n;
               }
            }

            for (int i = 0; i < count - 1; i++) {
               for (int j = 0; j < 6; j++) {
                  ix[0] = i;
                  ix[1] = top ? i + 1 : i;
                  ix[2] = i + 1;
                  ix[3] = top ? i : i + 1;
                  iy[0] = j;
                  iy[1] = top ? j : j + 1;
                  iy[2] = j + 1;
                  iy[3] = top ? j + 1 : j;
                  Vector3f a = points[ix[0]][iy[0]];
                  Vector3f b = points[ix[1]][iy[1]];
                  Vector3f c = points[ix[2]][iy[2]];
                  Vector3f area = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
                  if (area.lengthSquared() < 1.0E-10F) {
                     area = new Vector3f(c).sub(a).cross(new Vector3f(points[ix[3]][iy[3]]).sub(a));
                     if (area.lengthSquared() < 1.0E-10F) {
                        continue;
                     }
                  }

                  for (int k = 0; k < 4; k++) {
                     vertex(
                        s,
                        out,
                        light,
                        OverlayTexture.NO_OVERLAY,
                        colors[ix[k]][iy[k]],
                        new FacialMesh.V(points[ix[k]][iy[k]], 32.0F, 32.0F),
                        normals[ix[k]][iy[k]]
                     );
                  }
               }
            }
         }
      }
   }

   private static Vector3f lipPoint(float x, float v, boolean top, FacialData d, FacialPose p) {
      float taper = aperture(x, d, p);
      float edge = top ? upper(x, d, p) : lower(x, d, p);
      float spread = 1.0F / (1.0F + 0.45F * Math.max(0.0F, p.width() - 1.0F) + 0.35F * Math.max(0.0F, p.smile()));
      float pressure = p.contact().influence((x - d.x) / radius(d, p));
      float fullness = (top ? 0.34F : 0.44F) * spread * (1.0F - 0.12F * FacialExpression.ease((double)p.open())) * (1.0F - 0.28F * pressure);
      float thickness = (top ? d.upperLip : d.lowerLip) * taper * fullness;
      if (!top) {
         thickness = Math.min(thickness, Math.max(0.0F, chin(x, -4.0F, d, p) - edge) * 0.8F);
      }

      float y = edge + (float)(top ? -1 : 1) * thickness * v;
      float roll = (float)Math.sin(Math.PI * (double)v);
      roll *= roll;
      float z = deform(x, skinY(x, y, d, p), -4.0F, d, p).z - 0.007F - d.lipProjection * taper * fullness * 0.65F * roll * (1.0F - 0.35F * pressure);
      return new Vector3f(x, y, z);
   }

   public static void relaxedTongue(PoseStack s, VertexConsumer out, int light, FacialData d, FacialPose p, float amount) {
      relaxedTongue(s, out, light, d, p, new FacialTongue(amount, 0.0F, 0.0F));
   }

   public static void relaxedTongue(PoseStack s, VertexConsumer out, int light, FacialData d, FacialPose p, FacialTongue pose) {
      float show = pose.extension() * FacialData.clamp((p.open() - 0.25F) / 0.45F, 0.0F, 1.0F);
      if (d.tongue && !(show < 0.001F)) {
         float width = Math.min(0.54F, radius(d, p) * 0.6F) * (0.65F + 0.35F * show);
         float lip = lower(d.x, d, p);
         float root = lip - Math.min(0.35F, (lip - upper(d.x, d, p)) * 0.35F);
         float drop = 0.34F * show;
         Vector3f[][] top = new Vector3f[17][7];
         Vector3f[][] bottom = new Vector3f[17][7];

         for (int i = 0; i <= 16; i++) {
            for (int j = 0; j <= 6; j++) {
               float u = -1.0F + (float)i / 8.0F;
               float v = (float)j / 6.0F;
               float rounded = (float)Math.sqrt((double)Math.max(0.0F, 1.0F - u * u));
               Vector3f point = relaxedPoint(d, p, width, root, lip, drop, show, u, v, pose);
               top[i][j] = point;
               bottom[i][j] = new Vector3f(point).add(0.0F, 0.0F, 0.045F * show * rounded * (float)Math.sin((double)v * Math.PI));
            }
         }

         Vector3f[][] topNormals = tongueNormals(top, false);
         Vector3f[][] bottomNormals = tongueNormals(bottom, true);
         int[] colors = new int[17];
         int[] underColors = new int[17];

         for (int i = 0; i <= 16; i++) {
            float u = -1.0F + (float)i / 8.0F;
            float tone = 0.94F + 0.06F * (1.0F - Math.abs(u)) - 0.025F * (float)Math.exp((double)(-u * u / 0.02F));
            colors[i] = shade(d.tongueColor, tone);
            underColors[i] = shade(d.tongueColor, tone * 0.86F);
         }

         for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 6; j++) {
               tongueQuad(s, out, light, top, topNormals, colors, i, j, false);
               tongueQuad(s, out, light, bottom, bottomNormals, underColors, i, j, true);
            }
         }
      }
   }

   private static Vector3f[][] tongueNormals(Vector3f[][] points, boolean underside) {
      Vector3f[][] normals = new Vector3f[17][7];

      for (int i = 0; i <= 16; i++) {
         for (int j = 0; j <= 6; j++) {
            Vector3f across = new Vector3f(points[Math.min(16, i + 1)][j]).sub(points[Math.max(0, i - 1)][j]);
            Vector3f along = new Vector3f(points[i][Math.min(6, j + 1)]).sub(points[i][Math.max(0, j - 1)]);
            Vector3f normal = along.cross(across);
            if (normal.lengthSquared() < 1.0E-10F) {
               normal.set(0.0F, 0.0F, -1.0F);
            } else {
               normal.normalize();
            }

            if (underside) {
               normal.negate();
            }

            normals[i][j] = normal;
         }
      }

      return normals;
   }

   private static void tongueQuad(
      PoseStack s, VertexConsumer out, int light, Vector3f[][] points, Vector3f[][] normals, int[] colors, int i, int j, boolean flip
   ) {
      tongueVertex(s, out, light, points, normals, colors, i, j);
      tongueVertex(s, out, light, points, normals, colors, flip ? i + 1 : i, flip ? j : j + 1);
      tongueVertex(s, out, light, points, normals, colors, i + 1, j + 1);
      tongueVertex(s, out, light, points, normals, colors, flip ? i : i + 1, flip ? j + 1 : j);
   }

   private static void tongueVertex(PoseStack s, VertexConsumer out, int light, Vector3f[][] points, Vector3f[][] normals, int[] colors, int i, int j) {
      vertex(s, out, light, OverlayTexture.NO_OVERLAY, colors[i], new FacialMesh.V(points[i][j], 32.0F, 32.0F), normals[i][j]);
   }

   private static Vector3f relaxedPoint(
      FacialData d, FacialPose p, float width, float root, float lip, float drop, float show, float u, float v, FacialTongue pose
   ) {
      float rounded = (float)Math.sqrt((double)Math.max(0.0F, 1.0F - u * u));
      v *= FacialExpression.ease((double)(show / 0.3F));
      float travel = v * rounded;
      float bend = v * v * show;
      float x = d.x + width * u * (1.0F - 0.14F * v) + pose.side() * bend * rounded;
      float y = root + 0.035F * (1.0F - rounded) + (lip + drop - root) * travel - pose.curl() * bend * rounded;
      float faceZ = deform(x, skinY(x, y, d, p), -4.0F, d, p).z;
      float z = faceZ
         + 0.32F * d.depth * (1.0F - travel) * (1.0F - travel)
         - 0.018F
         - show * (0.1F + 0.2F * (float)Math.sin((double)v * Math.PI * 0.8)) * travel
         - pose.curl() * 0.18F * bend * rounded;
      float exit = FacialExpression.ease((double)((y - lower(x, d, p) + 0.12F) / 0.12F));
      float outside = faceZ - d.lipProjection * aperture(x, d, p) - 0.1F;
      z += (Math.min(z, outside) - z) * exit;
      return new Vector3f(x, y, z);
   }

   private static int shade(int color, float amount) {
      return 0xFF000000
         | (int)((float)(color >>> 16 & 0xFF) * amount) << 16
         | (int)((float)(color >>> 8 & 0xFF) * amount) << 8
         | (int)((float)(color & 0xFF) * amount);
   }

   public static void finger(PoseStack s, VertexConsumer out, int light, FacialData d, FacialPose p, float progress) {
      progress = FacialData.clamp(progress, 0.0F, 1.0F);
      if (!(progress < 0.005F) && !(p.open() < 0.05F)) {
         float top = upper(d.x, d, p);
         float bottom = lower(d.x, d, p);
         float y = (top + bottom) * 0.5F;
         float half = Math.min(0.14F, Math.min((bottom - top) * 0.28F, d.width * p.width() * 0.18F));
         float tip = -4.0F + d.depth * 0.55F - 2.0F * (1.0F - progress);
         float back = tip - 1.7F;
         int skin = -1789553;
         box(s, out, light, skin, d.x - half, y - half, back, d.x + half, y + half, tip);
         box(s, out, light, shade(skin, 0.94F), d.x - half * 1.05F, y - half * 1.05F, back - 0.8F, d.x + half * 1.05F, y + half * 1.05F, back);
         box(s, out, light, -731710, d.x - half * 0.7F, y - half - 0.006F, tip - 0.4F, d.x + half * 0.7F, y - half - 0.003F, tip - 0.08F);
      }
   }

   static void box(PoseStack s, VertexConsumer out, int light, int color, float x, float y, float z, float X, float Y, float Z) {
      Vector3f a = new Vector3f(x, y, z);
      Vector3f b = new Vector3f(X, y, z);
      Vector3f c = new Vector3f(X, Y, z);
      Vector3f d = new Vector3f(x, Y, z);
      Vector3f e = new Vector3f(x, y, Z);
      Vector3f f = new Vector3f(X, y, Z);
      Vector3f g = new Vector3f(X, Y, Z);
      Vector3f h = new Vector3f(x, Y, Z);
      quad(s, out, light, color, a, d, c, b);
      quad(s, out, light, color, e, f, g, h);
      quad(s, out, light, color, a, b, f, e);
      quad(s, out, light, color, d, h, g, c);
      quad(s, out, light, color, a, e, h, d);
      quad(s, out, light, color, b, c, g, f);
   }

   public static void brows(PoseStack s, VertexConsumer out, int light, FacialPose p, float spacing, float width, float eyeY) {
      if (!(Math.abs(p.brow()) < 0.001F)) {
         for (int sign : new int[]{-1, 1}) {
            float x = (float)sign * spacing;
            float w = width * 0.5F;
            float y = eyeY - 8.0F - 0.3F - Math.max(0.0F, p.brow()) * 0.5F;
            float slope = Math.min(0.0F, p.brow()) * (float)sign * 0.25F;
            quad(
               s,
               out,
               light,
               -11979975,
               new Vector3f(x - w, y - slope, -4.015F),
               new Vector3f(x - w, y - slope + 0.12F, -4.015F),
               new Vector3f(x + w, y + slope + 0.12F, -4.015F),
               new Vector3f(x + w, y + slope, -4.015F)
            );
         }
      }
   }

   private static void patch(PoseStack s, VertexConsumer out, int light, int overlay, int color, int nx, int ny, FacialMesh.Patch patch, boolean flip) {
      for (int x = 0; x < nx; x++) {
         for (int y = 0; y < ny; y++) {
            FacialMesh.V[] v = new FacialMesh.V[]{
               patch.at((float)x / (float)nx, (float)y / (float)ny),
               patch.at((float)x / (float)nx, (float)(y + 1) / (float)ny),
               patch.at((float)(x + 1) / (float)nx, (float)(y + 1) / (float)ny),
               patch.at((float)(x + 1) / (float)nx, (float)y / (float)ny)
            };
            Vector3f n = new Vector3f(v[1].p).sub(v[0].p).cross(new Vector3f(v[2].p).sub(v[0].p));
            if (!(n.lengthSquared() < 1.0E-10F)) {
               n.normalize();
               if (flip) {
                  n.negate();
               }

               for (FacialMesh.V vertex : v) {
                  vertex(s, out, light, overlay, color, vertex, n);
               }
            }
         }
      }
   }

   static void quad(PoseStack s, VertexConsumer out, int light, int color, Vector3f... points) {
      Vector3f normal = new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
      if (normal.lengthSquared() < 1.0E-10F) {
         normal = new Vector3f(points[2]).sub(points[0]).cross(new Vector3f(points[3]).sub(points[0]));
      }

      if (!(normal.lengthSquared() < 1.0E-10F)) {
         normal.normalize();

         for (Vector3f point : points) {
            vertex(s, out, light, OverlayTexture.NO_OVERLAY, color, new FacialMesh.V(point, 32.0F, 32.0F), normal);
         }
      }
   }

   private static void vertex(PoseStack s, VertexConsumer out, int light, int overlay, int color, FacialMesh.V v, Vector3f normal) {
      Vector3f point = s.last().pose().transformPosition(new Vector3f(v.p).div(16.0F));
      Vector3f n = s.last().transformNormal(normal.x, normal.y, normal.z, new Vector3f()).normalize();
      out.addVertex(point.x, point.y, point.z, color, v.u / 64.0F, v.v / 64.0F, overlay, light, n.x, n.y, n.z);
   }

   private FacialMesh() {
   }

   private interface Patch {
      FacialMesh.V at(float var1, float var2);
   }

   private static record V(Vector3f p, float u, float v) {
   }
}
