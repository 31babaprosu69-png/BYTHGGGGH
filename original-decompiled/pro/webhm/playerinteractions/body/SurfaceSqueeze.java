package pro.webhm.playerinteractions.body;

import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class SurfaceSqueeze {
   public boolean active;
   public float separateWeight;
   public final Vector3f center = new Vector3f();
   public final Vector3f side = new Vector3f(1.0F, 0.0F, 0.0F);
   public final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
   public final Vector3f front = new Vector3f(0.0F, 0.0F, 1.0F);
   public float halfWidth = 0.25F;
   public float height = 0.25F;
   public float thickness = 0.25F;
   public float press = 1.0F;
   public float brushRadius = 0.18F;
   public final Vector3f[] contact = new Vector3f[]{new Vector3f(), new Vector3f()};
   public final Vector3f[] contactNormal = new Vector3f[]{new Vector3f(-1.0F, 0.0F, 0.0F), new Vector3f(1.0F, 0.0F, 0.0F)};
   private final Vector3f tangent = new Vector3f();
   private final Vector3f bitangent = new Vector3f();
   private final Vector3f a = new Vector3f();
   private final Vector3f b = new Vector3f();
   private final Vector3f c = new Vector3f();
   private final Vector3f d = new Vector3f();

   public void frame(Matrix4fc frame, Vector3fc middle, float width, float radius) {
      this.center.set(middle);
      frame.transformDirection(this.side.set(1.0F, 0.0F, 0.0F)).normalize();
      frame.transformDirection(this.up.set(0.0F, 1.0F, 0.0F)).normalize();
      this.side.cross(this.up, this.front).normalize();
      this.front.cross(this.side, this.up).normalize();
      this.halfWidth = Math.max(0.08F, width);
      this.height = Math.max(0.1F, radius * 1.5F);
      this.thickness = Math.max(0.15F, radius * 1.5F);
      this.active = true;
   }

   private static float window(float value) {
      float t = Math.max(0.0F, 1.0F - value * value);
      return t * t * t;
   }

   public Vector3f displace(float x, float y, float z, float depth, SurfaceDent owner, Vector3f out) {
      float dx = x - this.center.x;
      float dy = y - this.center.y;
      float dz = z - this.center.z;
      float sx = dx * this.side.x + dy * this.side.y + dz * this.side.z;
      float sy = dx * this.up.x + dy * this.up.y + dz * this.up.z;
      float sz = dx * this.front.x + dy * this.front.y + dz * this.front.z;
      float edge = Math.max(0.0F, (Math.abs(sx) / this.halfWidth - 1.0F) / 0.5F);
      float separate = Math.max(0.0F, Math.min(1.0F, this.separateWeight));
      float protection = owner.protection(x, y, z);
      float blend = Math.max(0.0F, Math.min(1.0F, this.press)) * (1.0F - separate);
      float w = window(edge) * window(sy / this.height) * window(sz / this.thickness) * protection;
      float k = Math.min(0.18F, Math.max(0.0F, depth) / this.halfWidth) * w * blend;
      float bulge = 1.0F / (float)Math.sqrt((double)(1.0F - k)) - 1.0F;
      out.set(x, y, z).fma(-sx * k, this.side).fma(sy * bulge, this.up).fma(sz * bulge, this.front);
      if (blend < 1.0F) {
         for (int i = 0; i < 2; i++) {
            float px = x - this.contact[i].x;
            float py = y - this.contact[i].y;
            float pz = z - this.contact[i].z;
            Vector3f n = this.contactNormal[i];
            float h = px * n.x + py * n.y + pz * n.z;
            float r2 = Math.max(0.0F, px * px + py * py + pz * pz - h * h);
            float radius = Math.max(0.001F, this.brushRadius);
            float falloff = Math.max(0.0F, 1.0F - r2 / (radius * radius));
            float gain = 0.35F + 0.35F * Math.max(0.0F, Math.min(1.0F, this.press)) * separate;
            float localDepth = gain * depth;
            if (separate > 0.0F) {
               localDepth = Math.min(localDepth, radius * 0.18F);
            }

            float amount = localDepth * (1.0F - blend) * falloff * falloff * falloff * window(h / 0.1F) * protection;
            out.fma(-amount, n);
         }
      }

      return out;
   }

   public Vector3f normal(float x, float y, float z, Vector3fc original, float depth, SurfaceDent owner, Vector3f out) {
      this.tangent.set(Math.abs(original.y()) < 0.8F ? 0.0F : 1.0F, Math.abs(original.y()) < 0.8F ? 1.0F : 0.0F, 0.0F).cross(original).normalize();
      this.bitangent.set(original).cross(this.tangent).normalize();
      float e = 5.0E-4F;
      this.displace(x + e * this.tangent.x, y + e * this.tangent.y, z + e * this.tangent.z, depth, owner, this.a);
      this.displace(x - e * this.tangent.x, y - e * this.tangent.y, z - e * this.tangent.z, depth, owner, this.b);
      this.displace(x + e * this.bitangent.x, y + e * this.bitangent.y, z + e * this.bitangent.z, depth, owner, this.c);
      this.displace(x - e * this.bitangent.x, y - e * this.bitangent.y, z - e * this.bitangent.z, depth, owner, this.d);
      return this.a.sub(this.b).cross(this.c.sub(this.d), out).normalize();
   }

   public boolean near(float x0, float y0, float z0, float x1, float y1, float z1) {
      float x = (x0 + x1) * 0.5F - this.center.x;
      float y = (y0 + y1) * 0.5F - this.center.y;
      float z = (z0 + z1) * 0.5F - this.center.z;
      float ex = (x1 - x0) * 0.5F;
      float ey = (y1 - y0) * 0.5F;
      float ez = (z1 - z0) * 0.5F;
      if (this.separateWeight > 0.0F || this.press < 1.0F) {
         for (Vector3f p : this.contact) {
            float r = this.brushRadius + 0.1F;
            if (p.x >= x0 - r && p.x <= x1 + r && p.y >= y0 - r && p.y <= y1 + r && p.z >= z0 - r && p.z <= z1 + r) {
               return true;
            }
         }
      }

      return this.press > 0.0F
         && overlaps(this.side, 1.5F * this.halfWidth, x, y, z, ex, ey, ez)
         && overlaps(this.up, this.height, x, y, z, ex, ey, ez)
         && overlaps(this.front, this.thickness, x, y, z, ex, ey, ez);
   }

   private static boolean overlaps(Vector3fc axis, float range, float x, float y, float z, float ex, float ey, float ez) {
      return Math.abs(axis.x() * x + axis.y() * y + axis.z() * z) <= range + Math.abs(axis.x()) * ex + Math.abs(axis.y()) * ey + Math.abs(axis.z()) * ez;
   }
}
