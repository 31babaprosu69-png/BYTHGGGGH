package pro.webhm.playerinteractions.body;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class SurfaceDent {
   public final Vector3f center = new Vector3f();
   public final Vector3f normal = new Vector3f(0.0F, 0.0F, -1.0F);
   public float radius = 0.1F;
   public float depth;
   public final SurfaceSqueeze squeeze = new SurfaceSqueeze();
   private final Vector3f[] guards = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
   private final float[] guardRadii = new float[4];
   private int guardCount;
   private final float[][] rings = new float[4][24];
   private final float[][] ringBounds = new float[4][6];
   private int ringCount;

   public void clearGuards() {
      this.guardCount = this.ringCount = 0;
   }

   public void guardRings(Vector3f[] points) {
      if (this.ringCount != 4) {
         float[] ring = this.rings[this.ringCount];
         float[] bounds = this.ringBounds[this.ringCount++];

         for (int k = 0; k < 3; k++) {
            bounds[k] = Float.POSITIVE_INFINITY;
            bounds[k + 3] = Float.NEGATIVE_INFINITY;
         }

         for (int i = 0; i < 8; i++) {
            for (int k = 0; k < 3; k++) {
               float value = points[i].get(k);
               ring[i * 3 + k] = value;
               bounds[k] = Math.min(bounds[k], value);
               bounds[k + 3] = Math.max(bounds[k + 3], value);
            }
         }
      }
   }

   public void guard(Vector3fc at, float radius) {
      if (this.guardCount < 4) {
         this.guards[this.guardCount].set(at);
         this.guardRadii[this.guardCount++] = radius;
      }
   }

   public float protection(float x, float y, float z) {
      float result = 1.0F;

      for (int i = 0; i < this.guardCount; i++) {
         float d = this.guards[i].distance(x, y, z);
         float t = Math.max(0.0F, Math.min(1.0F, (d - this.guardRadii[i]) / 0.0625F));
         result = Math.min(result, t * t * (3.0F - 2.0F * t));
      }

      for (int j = 0; j < this.ringCount; j++) {
         float[] b = this.ringBounds[j];
         if (!(x < b[0] - 0.0625F) && !(x > b[3] + 0.0625F) && !(y < b[1] - 0.0625F) && !(y > b[4] + 0.0625F) && !(z < b[2] - 0.0625F) && !(z > b[5] + 0.0625F)
            )
          {
            float[] p = this.rings[j];
            float closest = Float.POSITIVE_INFINITY;

            for (int half = 0; half < 2; half++) {
               for (int edge = 0; edge < 4; edge++) {
                  int a = (half * 4 + (edge == 0 ? 0 : (edge == 1 ? 1 : (edge == 2 ? 3 : 2)))) * 3;
                  int c = (half * 4 + (edge == 0 ? 1 : (edge == 1 ? 3 : (edge == 2 ? 2 : 0)))) * 3;
                  float dx = p[c] - p[a];
                  float dy = p[c + 1] - p[a + 1];
                  float dz = p[c + 2] - p[a + 2];
                  float t = Math.max(
                     0.0F, Math.min(1.0F, ((x - p[a]) * dx + (y - p[a + 1]) * dy + (z - p[a + 2]) * dz) / Math.max(1.0E-12F, dx * dx + dy * dy + dz * dz))
                  );
                  float ax = x - p[a] - t * dx;
                  float ay = y - p[a + 1] - t * dy;
                  float az = z - p[a + 2] - t * dz;
                  closest = Math.min(closest, ax * ax + ay * ay + az * az);
               }
            }

            float t = Math.max(0.0F, Math.min(1.0F, ((float)Math.sqrt((double)closest) - 0.03125F) / 0.03125F));
            result = Math.min(result, t * t * (3.0F - 2.0F * t));
         }
      }

      return result;
   }

   public float amount(float x, float y, float z) {
      float dx = x - this.center.x;
      float dy = y - this.center.y;
      float dz = z - this.center.z;
      float h = dx * this.normal.x + dy * this.normal.y + dz * this.normal.z;
      float r2 = dx * dx + dy * dy + dz * dz - h * h;
      float rad = Math.max(0.001F, this.radius);
      float r = 1.0F - r2 / (rad * rad);
      if (!(r <= 0.0F) && !(Math.abs(h) >= 0.16F)) {
         float t = Math.max(0.0F, (Math.abs(h) - 0.05F) / 0.11F);
         float band = 1.0F - t * t * (3.0F - 2.0F * t);
         return this.depth * r * r * r * band * this.protection(x, y, z);
      } else {
         return 0.0F;
      }
   }

   public Vector3f displace(float x, float y, float z, Vector3f out) {
      return this.squeeze.active ? this.squeeze.displace(x, y, z, this.depth, this, out) : out.set(x, y, z).fma(-this.amount(x, y, z), this.normal);
   }

   public Vector3f normal(float x, float y, float z, Vector3fc original, Vector3f out) {
      if (this.squeeze.active) {
         return this.squeeze.normal(x, y, z, original, this.depth, this, out);
      } else {
         float step = 1.0E-4F;
         float gx = (this.amount(x + step, y, z) - this.amount(x - step, y, z)) / (2.0F * step);
         float gy = (this.amount(x, y + step, z) - this.amount(x, y - step, z)) / (2.0F * step);
         float gz = (this.amount(x, y, z + step) - this.amount(x, y, z - step)) / (2.0F * step);
         float denominator = 1.0F - (this.normal.x * gx + this.normal.y * gy + this.normal.z * gz);
         float k = this.normal.dot(original) / Math.max(0.2F, denominator);
         return out.set(original).add(k * gx, k * gy, k * gz).normalize();
      }
   }

   public boolean near(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      if (this.squeeze.active) {
         return (double)this.depth > 1.0E-7 && this.squeeze.near(minX, minY, minZ, maxX, maxY, maxZ);
      } else {
         float reach = this.radius + 0.16F;
         return (double)this.depth > 1.0E-7
            && this.center.x >= minX - reach
            && this.center.x <= maxX + reach
            && this.center.y >= minY - reach
            && this.center.y <= maxY + reach
            && this.center.z >= minZ - reach
            && this.center.z <= maxZ + reach;
      }
   }
}
