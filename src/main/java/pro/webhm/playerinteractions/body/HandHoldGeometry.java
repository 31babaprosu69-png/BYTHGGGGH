package pro.webhm.playerinteractions.body;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class HandHoldGeometry {
   public static Vector3f playerHand(int side, float yaw) {
      return new Matrix4f()
         .rotateY((float)Math.toRadians((double)(180.0F - yaw)))
         .scale(-0.9375F, -0.9375F, 0.9375F)
         .translate(0.0F, -1.501F, 0.0F)
         .scale(0.0625F)
         .translate((float)(-side * 5), 2.0F, 0.0F)
         .rotateZ((float)Math.toRadians((double)(side * 12)))
         .rotateX((float)Math.toRadians(-12.0))
         .transformPosition(new Vector3f((float)(-side), 10.0F, 0.0F));
   }

   public static HandHoldGeometry.Reach reach(Vector3f target) {
      float length = Math.max(0.001F, target.length());
      float d = Math.max(2.001F, Math.min(9.999F, length));
      float pitch = (float)Math.atan2((double)target.z, Math.hypot((double)target.x, (double)target.y));
      float roll = (float)Math.atan2((double)(-target.x), (double)target.y);
      float shoulder = (float)Math.acos((double)Math.max(-1.0F, Math.min(1.0F, (16.0F + d * d - 36.0F) / (8.0F * d))));
      float elbow = -((float)Math.acos((double)Math.max(-1.0F, Math.min(1.0F, (d * d - 16.0F - 36.0F) / 48.0F))));
      return new HandHoldGeometry.Reach(pitch + shoulder, roll, elbow);
   }

   public static record Reach(float pitch, float roll, float elbow) {
   }
}
