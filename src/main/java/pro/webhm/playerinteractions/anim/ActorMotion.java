package pro.webhm.playerinteractions.anim;

import net.minecraft.util.Mth;

public class ActorMotion {
   public float x;
   public float y;
   public float z;
   public float yaw;
   public boolean leadPlayer;
   public Float groundOffset;

   public ActorMotion() {
   }

   public ActorMotion(float x, float y, float z, float yaw, boolean leadPlayer) {
      this(x, y, z, yaw, leadPlayer, null);
   }

   public ActorMotion(float x, float y, float z, float yaw, boolean leadPlayer, Float groundOffset) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.leadPlayer = leadPlayer;
      this.groundOffset = groundOffset;
   }

   public float groundOffset() {
      return Mth.clamp(this.groundOffset == null ? 0.0F : this.groundOffset, 0.0F, 20.0F);
   }

   public ActorMotion copy() {
      return new ActorMotion(this.x, this.y, this.z, this.yaw, this.leadPlayer, this.groundOffset);
   }

   public ActorMotion rebase(double ox, double oy, double oz, float heading, double nx, double ny, double nz, float nextHeading) {
      double a = Math.toRadians((double)heading);
      double b = Math.toRadians((double)nextHeading);
      double dx = ox + (double)this.x * Math.cos(a) - (double)this.z * Math.sin(a) - nx;
      double dz = oz + (double)this.z * Math.cos(a) + (double)this.x * Math.sin(a) - nz;
      return new ActorMotion(
         (float)(dx * Math.cos(b) + dz * Math.sin(b)),
         (float)(oy + (double)this.y - ny),
         (float)(dz * Math.cos(b) - dx * Math.sin(b)),
         Mth.wrapDegrees(heading + this.yaw - nextHeading),
         this.leadPlayer,
         this.groundOffset
      );
   }

   public static ActorMotion lerp(ActorMotion a, ActorMotion b, float t) {
      ActorMotion left = a == null ? new ActorMotion() : a;
      ActorMotion right = b == null ? new ActorMotion() : b;
      return new ActorMotion(
         Mth.lerp(t, left.x, right.x),
         Mth.lerp(t, left.y, right.y),
         Mth.lerp(t, left.z, right.z),
         Mth.rotLerp(t, left.yaw, right.yaw),
         t >= 0.999F ? right.leadPlayer : left.leadPlayer,
         Mth.lerp(t, left.groundOffset(), right.groundOffset())
      );
   }
}
