package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.body.SurfaceDent;

public final class SurfaceDentMesh implements VertexConsumer {
   private final float[][] data = new float[4][16];
   private final Vector3f[] local = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
   private final Matrix4f matrix = new Matrix4f();
   private final Matrix4f inverse = new Matrix4f();
   private final Matrix3f normals = new Matrix3f();
   private final Matrix3f inverseNormals = new Matrix3f();
   private final Vector3f at = new Vector3f();
   private final Vector3f normal = new Vector3f();
   private final Vector3f original = new Vector3f();
   private VertexConsumer out;
   private SurfaceDent dent;
   private int count;

   public SurfaceDentMesh begin(VertexConsumer out, Matrix4fc base, SurfaceDent dent) {
      if (this.count != 0) {
         throw new IllegalStateException("Incomplete surface quad");
      } else {
         this.out = out;
         this.dent = dent;
         this.matrix.set(base);
         this.inverse.set(this.matrix).invert();
         this.normals.set(this.matrix).invert().transpose();
         this.inverseNormals.set(this.normals).invert();
         return this;
      }
   }

   public VertexConsumer vertex(double x0, double y0, double z0) {
      float x = (float)x0;
      float y = (float)y0;
      float z = (float)z0;
      float[] d = this.data[this.count];
      d[0] = x;
      d[1] = y;
      d[2] = z;
      return this;
   }

   public VertexConsumer color(int r, int g, int b, int a) {
      float[] d = this.data[this.count];
      d[3] = (float)r;
      d[4] = (float)g;
      d[5] = (float)b;
      d[6] = (float)a;
      return this;
   }

   public VertexConsumer uv(float u, float v) {
      this.data[this.count][7] = u;
      this.data[this.count][8] = v;
      return this;
   }

   public VertexConsumer overlayCoords(int u, int v) {
      this.data[this.count][9] = (float)u;
      this.data[this.count][10] = (float)v;
      return this;
   }

   public VertexConsumer uv2(int u, int v) {
      this.data[this.count][11] = (float)u;
      this.data[this.count][12] = (float)v;
      return this;
   }

   public VertexConsumer normal(float x, float y, float z) {
      float[] d = this.data[this.count];
      d[13] = x;
      d[14] = y;
      d[15] = z;
      if (++this.count == 4) {
         this.quad();
         this.count = 0;
      }

      return this;
   }

   private void raw(float[] d) {
      this.out
         .vertex(d[0], d[1], d[2])
         .color((int)d[3], (int)d[4], (int)d[5], (int)d[6])
         .uv(d[7], d[8])
         .overlayCoords((int)d[9], (int)d[10])
         .uv2((int)d[11], (int)d[12])
         .normal(d[13], d[14], d[15]).endVertex();
   }

   private void quad() {
      if ((double)this.dent.depth <= 1.0E-7) {
         for (float[] d : this.data) {
            this.raw(d);
         }
      } else if (this.data[2][0] == this.data[3][0] && this.data[2][1] == this.data[3][1] && this.data[2][2] == this.data[3][2]) {
         for (float[] d : this.data) {
            this.raw(d);
         }
      } else {
         float x0 = Float.POSITIVE_INFINITY;
         float y0 = x0;
         float z0 = x0;
         float x1 = Float.NEGATIVE_INFINITY;
         float y1 = x1;
         float z1 = x1;
         boolean protectedQuad = true;

         for (int i = 0; i < 4; i++) {
            Vector3f p = this.inverse.transformPosition(this.local[i].set(this.data[i][0], this.data[i][1], this.data[i][2]));
            x0 = Math.min(x0, p.x);
            y0 = Math.min(y0, p.y);
            z0 = Math.min(z0, p.z);
            x1 = Math.max(x1, p.x);
            y1 = Math.max(y1, p.y);
            z1 = Math.max(z1, p.z);
            protectedQuad &= this.dent.protection(p.x, p.y, p.z) <= 0.0F;
         }

         if (!protectedQuad && this.dent.near(x0, y0, z0, x1, y1, z1)) {
            int nx = Math.min(48, Math.max(1, (int)Math.ceil((double)(this.local[0].distance(this.local[1]) * 32.0F - 1.0E-4F))));
            int ny = Math.min(48, Math.max(1, (int)Math.ceil((double)(this.local[0].distance(this.local[3]) * 32.0F - 1.0E-4F))));

            for (int y = 0; y < ny; y++) {
               for (int x = 0; x < nx; x++) {
                  this.emit((float)x / (float)nx, (float)y / (float)ny);
                  this.emit(((float)x + 1.0F) / (float)nx, (float)y / (float)ny);
                  this.emit(((float)x + 1.0F) / (float)nx, ((float)y + 1.0F) / (float)ny);
                  this.emit((float)x / (float)nx, ((float)y + 1.0F) / (float)ny);
               }
            }
         } else {
            for (float[] d : this.data) {
               this.raw(d);
            }
         }
      }
   }

   private float blend(int i, float u, float v) {
      float a = this.data[0][i] + (this.data[1][i] - this.data[0][i]) * u;
      float b = this.data[3][i] + (this.data[2][i] - this.data[3][i]) * u;
      return a + (b - a) * v;
   }

   private void position(float u, float v, Vector3f out) {
      out.set(this.local[0]).mul((1.0F - u) * (1.0F - v)).fma(u * (1.0F - v), this.local[1]).fma(u * v, this.local[2]).fma((1.0F - u) * v, this.local[3]);
   }

   private void emit(float u, float v) {
      this.position(u, v, this.at);
      this.inverseNormals.transform(this.original.set(this.blend(13, u, v), this.blend(14, u, v), this.blend(15, u, v))).normalize();
      if (this.dent.protection(this.at.x, this.at.y, this.at.z) <= 0.0F) {
         this.normal.set(this.blend(13, u, v), this.blend(14, u, v), this.blend(15, u, v));
      } else {
         this.dent.normal(this.at.x, this.at.y, this.at.z, this.original, this.normal);
         this.normals.transform(this.normal).normalize();
      }

      this.dent.displace(this.at.x, this.at.y, this.at.z, this.at);
      this.matrix.transformPosition(this.at);
      this.out
         .vertex(this.at.x, this.at.y, this.at.z)
         .color(Math.round(this.blend(3, u, v)), Math.round(this.blend(4, u, v)), Math.round(this.blend(5, u, v)), Math.round(this.blend(6, u, v)))
         .uv(this.blend(7, u, v), this.blend(8, u, v))
         .overlayCoords(Math.round(this.blend(9, u, v)), Math.round(this.blend(10, u, v)))
         .uv2(Math.round(this.blend(11, u, v)), Math.round(this.blend(12, u, v)))
         .normal(this.normal.x, this.normal.y, this.normal.z).endVertex();
   }

   public void endVertex() {
   }

   public void defaultColor(int r, int g, int b, int a) {
   }

   public void unsetDefaultColor() {
   }
}
