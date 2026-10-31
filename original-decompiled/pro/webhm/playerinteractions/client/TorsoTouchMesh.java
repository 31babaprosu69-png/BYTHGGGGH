package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.body.TorsoContact;

public final class TorsoTouchMesh implements VertexConsumer {
   private final float[][] data = new float[4][16];
   private final Vector3f[] local = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
   private final Matrix4f matrix = new Matrix4f();
   private final Matrix4f inverse = new Matrix4f();
   private final Matrix3f normals = new Matrix3f();
   private final Vector3f at = new Vector3f();
   private final Vector3f normal = new Vector3f();
   private final Vector3f faceNormal = new Vector3f();
   private VertexConsumer out;
   private TorsoContact dent;
   private int count;

   public TorsoTouchMesh begin(VertexConsumer out, Matrix4fc posedBone, TorsoContact dent) {
      if (this.count != 0) {
         throw new IllegalStateException("Incomplete torso quad");
      } else {
         this.out = out;
         this.dent = dent;
         this.matrix.set(posedBone).scale(0.0625F);
         this.inverse.set(this.matrix).invert();
         this.normals.set(this.matrix).invert().transpose();
         dent.normal(this.faceNormal);
         this.normals.transform(this.faceNormal).normalize();
         return this;
      }
   }

   public VertexConsumer addVertex(float x, float y, float z) {
      float[] d = this.data[this.count];
      d[0] = x;
      d[1] = y;
      d[2] = z;
      return this;
   }

   public VertexConsumer setColor(int r, int g, int b, int a) {
      float[] d = this.data[this.count];
      d[3] = (float)r;
      d[4] = (float)g;
      d[5] = (float)b;
      d[6] = (float)a;
      return this;
   }

   public VertexConsumer setUv(float u, float v) {
      this.data[this.count][7] = u;
      this.data[this.count][8] = v;
      return this;
   }

   public VertexConsumer setUv1(int u, int v) {
      this.data[this.count][9] = (float)u;
      this.data[this.count][10] = (float)v;
      return this;
   }

   public VertexConsumer setUv2(int u, int v) {
      this.data[this.count][11] = (float)u;
      this.data[this.count][12] = (float)v;
      return this;
   }

   public VertexConsumer setNormal(float x, float y, float z) {
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
         .addVertex(d[0], d[1], d[2])
         .setColor((int)d[3], (int)d[4], (int)d[5], (int)d[6])
         .setUv(d[7], d[8])
         .setUv1((int)d[9], (int)d[10])
         .setUv2((int)d[11], (int)d[12])
         .setNormal(d[13], d[14], d[15]);
   }

   private void quad() {
      if (!(this.dent.depth <= 1.0E-5F) && !(this.faceNormal.dot(this.data[0][13], this.data[0][14], this.data[0][15]) < 0.98F)) {
         for (int i = 0; i < 4; i++) {
            this.inverse.transformPosition(this.local[i].set(this.data[i][0], this.data[i][1], this.data[i][2]));
         }

         int nx = Math.min(32, Math.max(1, (int)Math.ceil((double)(this.local[0].distance(this.local[1]) * 2.0F - 1.0E-4F))));
         int ny = Math.min(24, Math.max(1, (int)Math.ceil((double)(this.local[0].distance(this.local[3]) * 2.0F - 1.0E-4F))));

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

   private float blend(int i, float u, float v) {
      return (this.data[0][i] * (1.0F - u) + this.data[1][i] * u) * (1.0F - v) + (this.data[3][i] * (1.0F - u) + this.data[2][i] * u) * v;
   }

   private void emit(float u, float v) {
      this.at.set(this.local[0]).mul((1.0F - u) * (1.0F - v)).fma(u * (1.0F - v), this.local[1]).fma(u * v, this.local[2]).fma((1.0F - u) * v, this.local[3]);
      this.dent.deformedNormal(this.at.x, this.at.y, this.at.z, this.normal);
      this.normals.transform(this.normal).normalize();
      this.dent.displaced(this.at.x, this.at.y, this.at.z, this.at);
      this.matrix.transformPosition(this.at);
      this.out
         .addVertex(this.at.x, this.at.y, this.at.z)
         .setColor(Math.round(this.blend(3, u, v)), Math.round(this.blend(4, u, v)), Math.round(this.blend(5, u, v)), Math.round(this.blend(6, u, v)))
         .setUv(this.blend(7, u, v), this.blend(8, u, v))
         .setUv1(Math.round(this.blend(9, u, v)), Math.round(this.blend(10, u, v)))
         .setUv2(Math.round(this.blend(11, u, v)), Math.round(this.blend(12, u, v)))
         .setNormal(this.normal.x, this.normal.y, this.normal.z);
   }
}
