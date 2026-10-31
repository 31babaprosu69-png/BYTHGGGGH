package pro.webhm.playerinteractions.client;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class SceneStrokePointer {
   public final Vector3f point = new Vector3f();
   public final Vector3f normal = new Vector3f();
   private final Vector3f ray = new Vector3f();
   private final SurfaceSnapshot.Hit hit = new SurfaceSnapshot.Hit();
   private float depth;

   public boolean pick(SurfaceSnapshot mesh, Vector3fc eye, Vector3fc aim) {
      this.ray.set(aim).sub(eye);
      float length = this.ray.length();
      if (length < 1.0E-5F) {
         return false;
      } else if (mesh.ray(eye, this.ray, 16.0F, this.hit)) {
         this.point.set(this.hit.point);
         this.normal.set(this.hit.normal);
         this.depth = this.hit.distance;
         return true;
      } else {
         if (this.depth <= 0.0F) {
            this.depth = length;
         }

         this.ray.div(length);
         this.point.set(eye).fma(this.depth, this.ray);
         this.normal.set(this.ray).negate();
         return false;
      }
   }
}
