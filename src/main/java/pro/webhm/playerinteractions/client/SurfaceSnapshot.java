package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class SurfaceSnapshot implements VertexConsumer {
   private float[] vertices = new float[32768];
   private float[] bounds = new float[6144];
   private int count;
   private SurfaceSnapshot.Alpha alpha = (u, v) -> true;
   private final Vector3f edge1 = new Vector3f();
   private final Vector3f edge2 = new Vector3f();
   private final Vector3f p = new Vector3f();
   private final Vector3f q = new Vector3f();
   private final Vector3f t = new Vector3f();
   private final Vector3f direction = new Vector3f();

   public void reset(SurfaceSnapshot.Alpha alpha) {
      this.count = 0;
      this.alpha = alpha;
   }

   public int quads() {
      return this.count / 4;
   }

   public int capacity() {
      return this.vertices.length * 4 + this.bounds.length * 4;
   }

   private void capacityForVertex() {
      if ((this.count + 1) * 8 > this.vertices.length) {
         this.vertices = Arrays.copyOf(this.vertices, this.vertices.length * 2);
      }

      if ((this.count / 4 + 1) * 6 > this.bounds.length) {
         this.bounds = Arrays.copyOf(this.bounds, this.bounds.length * 2);
      }
   }

   public VertexConsumer vertex(double x0, double y0, double z0) {
      float x = (float)x0;
      float y = (float)y0;
      float z = (float)z0;
      this.capacityForVertex();
      int i = this.count * 8;
      this.vertices[i] = x;
      this.vertices[i + 1] = y;
      this.vertices[i + 2] = z;
      return this;
   }

   public VertexConsumer color(int r, int g, int b, int a) {
      return this;
   }

   public VertexConsumer uv(float u, float v) {
      this.vertices[this.count * 8 + 6] = u;
      this.vertices[this.count * 8 + 7] = v;
      return this;
   }

   public VertexConsumer overlayCoords(int u, int v) {
      return this;
   }

   public VertexConsumer uv2(int u, int v) {
      return this;
   }

   public VertexConsumer normal(float x, float y, float z) {
      int i = this.count * 8;
      this.vertices[i + 3] = x;
      this.vertices[i + 4] = y;
      this.vertices[i + 5] = z;
      this.count++;
      if (this.count % 4 == 0) {
         int b = (this.count / 4 - 1) * 6;

         for (int k = 0; k < 3; k++) {
            float lo = Float.POSITIVE_INFINITY;
            float hi = Float.NEGATIVE_INFINITY;

            for (int j = this.count - 4; j < this.count; j++) {
               float a = this.vertices[j * 8 + k];
               lo = Math.min(lo, a);
               hi = Math.max(hi, a);
            }

            this.bounds[b + k] = lo;
            this.bounds[b + 3 + k] = hi;
         }
      }

      return this;
   }

   public boolean ray(Vector3fc origin, Vector3fc dir, float max, SurfaceSnapshot.Hit hit) {
      return this.ray(origin, dir, max, hit, true);
   }

   public boolean ray(Vector3fc origin, Vector3fc dir, float max, SurfaceSnapshot.Hit hit, boolean frontOnly) {
      return this.ray(origin, dir, max, hit, frontOnly, null);
   }

   public boolean ray(Vector3fc origin, Vector3fc dir, float max, SurfaceSnapshot.Hit hit, boolean frontOnly, SurfaceSnapshot.HitFilter filter) {
      if (Float.isFinite(max) && !(max <= 0.0F) && origin.isFinite() && dir.isFinite() && !((double)dir.lengthSquared() < 1.0E-10)) {
         this.direction.set(dir).normalize();
         float closest = max;
         boolean found = false;

         for (int quad = 0; quad < this.count / 4; quad++) {
            if (this.box(quad * 6, origin, this.direction, closest)) {
               int base = quad * 32;

               for (int tri = 0; tri < 2; tri++) {
                  int b = base + (tri == 0 ? 8 : 16);
                  int c = base + (tri == 0 ? 16 : 24);
                  this.edge1
                     .set(
                        this.vertices[b] - this.vertices[base], this.vertices[b + 1] - this.vertices[base + 1], this.vertices[b + 2] - this.vertices[base + 2]
                     );
                  this.edge2
                     .set(
                        this.vertices[c] - this.vertices[base], this.vertices[c + 1] - this.vertices[base + 1], this.vertices[c + 2] - this.vertices[base + 2]
                     );
                  this.p.set(this.direction).cross(this.edge2);
                  float det = this.edge1.dot(this.p);
                  if (!((double)Math.abs(det) < 1.0E-9)) {
                     this.t.set(origin).sub(this.vertices[base], this.vertices[base + 1], this.vertices[base + 2]);
                     float u = this.t.dot(this.p) / det;
                     if (!(u < -1.0E-6F) && !(u > 1.000001F)) {
                        this.q.set(this.t).cross(this.edge1);
                        float v = this.direction.dot(this.q) / det;
                        if (!(v < -1.0E-6F) && !(u + v > 1.000001F)) {
                           float d = this.edge2.dot(this.q) / det;
                           if (!(d < 1.0E-5F) && !(d > closest)) {
                              u = Math.max(0.0F, Math.min(1.0F, u));
                              v = Math.max(0.0F, Math.min(1.0F - u, v));
                              float nx = (1.0F - u - v) * this.vertices[base + 3] + u * this.vertices[b + 3] + v * this.vertices[c + 3];
                              float ny = (1.0F - u - v) * this.vertices[base + 4] + u * this.vertices[b + 4] + v * this.vertices[c + 4];
                              float nz = (1.0F - u - v) * this.vertices[base + 5] + u * this.vertices[b + 5] + v * this.vertices[c + 5];
                              if (!frontOnly || !(nx * this.direction.x + ny * this.direction.y + nz * this.direction.z > -1.0E-4F)) {
                                 float tu = (1.0F - u - v) * this.vertices[base + 6] + u * this.vertices[b + 6] + v * this.vertices[c + 6];
                                 float tv = (1.0F - u - v) * this.vertices[base + 7] + u * this.vertices[b + 7] + v * this.vertices[c + 7];
                                 if (this.alpha.opaque(tu, tv)) {
                                    float hx = origin.x() + d * this.direction.x;
                                    float hy = origin.y() + d * this.direction.y;
                                    float hz = origin.z() + d * this.direction.z;
                                    if (filter == null || filter.accept(hx, hy, hz, nx, ny, nz)) {
                                       closest = d;
                                       hit.distance = d;
                                       hit.u = tu;
                                       hit.v = tv;
                                       hit.point.set(origin).fma(d, this.direction);
                                       hit.normal.set(nx, ny, nz).normalize();
                                       found = true;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return found;
      } else {
         return false;
      }
   }

   private boolean box(int b, Vector3fc o, Vector3fc d, float max) {
      float lo = 0.0F;
      float hi = max;

      for (int k = 0; k < 3; k++) {
         float v = d.get(k);
         float a = o.get(k);
         if ((double)Math.abs(v) < 1.0E-9) {
            if (a < this.bounds[b + k] - 1.0E-5F || a > this.bounds[b + 3 + k] + 1.0E-5F) {
               return false;
            }
         } else {
            float x = (this.bounds[b + k] - a) / v;
            float y = (this.bounds[b + 3 + k] - a) / v;
            lo = Math.max(lo, Math.min(x, y));
            hi = Math.min(hi, Math.max(x, y));
            if (lo > hi + 1.0E-5F) {
               return false;
            }
         }
      }

      return true;
   }

   @FunctionalInterface
   public interface Alpha {
      boolean opaque(float var1, float var2);
   }

   public static final class Hit {
      public final Vector3f point = new Vector3f();
      public final Vector3f normal = new Vector3f();
      public float distance;
      public float u;
      public float v;
   }

   @FunctionalInterface
   public interface HitFilter {
      boolean accept(float var1, float var2, float var3, float var4, float var5, float var6);
   }

   public void endVertex() {
   }

   public void defaultColor(int r, int g, int b, int a) {
   }

   public void unsetDefaultColor() {
   }
}
