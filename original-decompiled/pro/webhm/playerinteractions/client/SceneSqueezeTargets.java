package pro.webhm.playerinteractions.client;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class SceneSqueezeTargets {
   public final SurfaceSnapshot.Hit[] hits = new SurfaceSnapshot.Hit[]{new SurfaceSnapshot.Hit(), new SurfaceSnapshot.Hit()};
   public final boolean[] valid = new boolean[2];
   public final Vector3f middle = new Vector3f();
   public float halfWidth = 0.25F;
   private final Matrix4f inverse = new Matrix4f();
   private final Vector3f local = new Vector3f();
   private final Vector3f origin = new Vector3f();
   private final Vector3f direction = new Vector3f();
   private final Vector3f side = new Vector3f();
   private final SurfaceSnapshot.HitFilter exit = (x, y, z, nx, ny, nz) -> nx * this.direction.x + ny * this.direction.y + nz * this.direction.z > 0.05F;
   private final Matrix4f volumeInverse = new Matrix4f();
   private final Vector3f volumePoint = new Vector3f();

   public boolean pick(SceneTouchSurface surface, Matrix4fc frame, Vector3fc anchor, float spread, float vertical, float area) {
      if (surface.hasVolumes) {
         this.inverse.set(frame).invert().transformPosition(this.local.set(anchor));
         this.local.x = 0.0F;
         this.local.y += vertical * area / 64.0F;
         frame.transformPosition(this.local, this.middle);
         frame.transformDirection(this.side.set(1.0F, 0.0F, 0.0F)).normalize();
         boolean row = true;

         for (int i = 0; i < 2; i++) {
            this.volumeInverse.set(surface.volumeFrames[i]).invert().transformPosition(this.volumePoint.set(this.middle));
            row &= this.volumePoint.y >= -0.025F && this.volumePoint.y <= 0.3375F;
            surface.volumeFrames[i]
               .transformPosition(this.origin.set((float)(i == 0 ? -2 : 2) / 16.0F, Math.max(0.015F, Math.min(0.30625F, this.volumePoint.y)), -0.203125F));
            float sign = i == 0 ? -1.0F : 1.0F;
            frame.transformDirection(this.direction.set(sign * (1.2F + 0.2F * spread), 0.0F, -(0.3F - 0.1F * spread)).normalize());
            this.valid[i] = surface.volumes[i].ray(this.origin, this.direction, 0.65F, this.hits[i], false, this.exit);
         }

         if (row && this.valid[0] && this.valid[1]) {
            this.halfWidth = Math.max(0.08F, Math.abs(this.local.set(this.hits[1].point).sub(this.hits[0].point).dot(this.side)) * 0.5F);
            this.middle.set(this.hits[0].point).add(this.hits[1].point).mul(0.5F);
            return true;
         }
      }

      return this.pick(surface.mesh, frame, anchor, spread, vertical, area);
   }

   public boolean pick(SurfaceSnapshot mesh, Matrix4fc frame, Vector3fc anchor, float spread, float vertical, float area) {
      this.inverse.set(frame).invert().transformPosition(this.local.set(anchor));
      this.local.x = 0.0F;
      this.local.y += vertical * area / 64.0F;
      this.local.z += 0.1F;
      frame.transformPosition(this.local, this.middle);
      frame.transformDirection(this.side.set(1.0F, 0.0F, 0.0F)).normalize();

      for (int i = 0; i < 2; i++) {
         float sign = i == 0 ? -1.0F : 1.0F;
         this.direction.set(sign * (1.2F + 0.2F * spread), 0.0F, -(0.3F - 0.1F * spread)).normalize();
         frame.transformDirection(this.direction).normalize();
         this.origin.set(this.middle);
         this.valid[i] = mesh.ray(this.origin, this.direction, 0.65F, this.hits[i], false, this.exit);
         if (!this.valid[i]) {
            this.hits[i].point.set(this.middle).fma(sign * 0.25F, this.side);
            frame.transformDirection(this.hits[i].normal.set(sign, 0.0F, -0.4F)).normalize();
         }
      }

      if (this.valid[0] && this.valid[1]) {
         this.halfWidth = Math.max(0.08F, Math.abs(this.local.set(this.hits[1].point).sub(this.hits[0].point).dot(this.side)) * 0.5F);
         this.middle.set(this.hits[0].point).add(this.hits[1].point).mul(0.5F);
         return this.hits[0].normal.dot(this.side) < -0.1F && this.hits[1].normal.dot(this.side) > 0.1F;
      } else {
         return false;
      }
   }
}
