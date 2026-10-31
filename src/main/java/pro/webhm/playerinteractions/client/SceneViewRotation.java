package pro.webhm.playerinteractions.client;

import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.NpcBone;

public final class SceneViewRotation {
   private static final float RAD = (float) (Math.PI / 180.0);

   public static Vector3f look(Vector3f basis, float yaw, float pitch) {
      return angles(
         new Quaternionf()
            .rotationYXZ((180.0F - basis.x) * (float) (Math.PI / 180.0), -basis.y * (float) (Math.PI / 180.0), -basis.z * (float) (Math.PI / 180.0))
            .rotateY(-yaw * (float) (Math.PI / 180.0))
            .rotateX(-pitch * (float) (Math.PI / 180.0))
      );
   }

   private static BoneTransform part(Map<String, BoneTransform> pose, NpcBone bone) {
      return pose.getOrDefault(bone.id, new BoneTransform());
   }

   public static Quaternionf root(Map<String, BoneTransform> pose) {
      BoneTransform r = part(pose, NpcBone.ROOT);
      return new Quaternionf().rotationZYX(r.rz * (float) (Math.PI / 180.0), -r.ry * (float) (Math.PI / 180.0), -r.rx * (float) (Math.PI / 180.0));
   }

   public static Quaternionf head(Map<String, BoneTransform> pose) {
      BoneTransform b = part(pose, NpcBone.BODY);
      BoneTransform c = part(pose, NpcBone.CHEST);
      BoneTransform h = part(pose, NpcBone.HEAD);
      return new Quaternionf()
         .rotationZYX((b.rz + h.rz) * (float) (Math.PI / 180.0), -(b.ry + c.ry + h.ry) * (float) (Math.PI / 180.0), -(b.rx + h.rx) * (float) (Math.PI / 180.0));
   }

   public static Quaternionf orientation(Map<String, BoneTransform> pose, float facing) {
      return new Quaternionf().rotationY((180.0F - facing) * (float) (Math.PI / 180.0)).mul(root(pose)).mul(head(pose)).normalize();
   }

   public static Vector3f eye(Map<String, BoneTransform> pose, float yaw, Vector3f out) {
      BoneTransform r = part(pose, NpcBone.ROOT);
      Quaternionf facing = new Quaternionf().rotationY((180.0F - yaw) * (float) (Math.PI / 180.0));
      out.set(headCentre(pose)).mul(0.9375F);
      out.y += 0.7071876F;
      root(pose).transform(out);
      facing.transform(out);
      return out.add(facing.transform(new Vector3f(-r.shiftX / 16.0F, r.height / 16.0F, r.shiftZ / 16.0F))).add(0.0F, 0.7F, 0.0F);
   }

   public static Vector3f headCentre(Map<String, BoneTransform> pose) {
      return head(pose).transform(new Vector3f(0.0F, 0.25F, 0.0F));
   }

   public static Vector3f angles(Map<String, BoneTransform> pose, float facing) {
      return angles(orientation(pose, facing));
   }

   public static Vector3f angles(Quaternionf orientation) {
      Matrix3f m = new Matrix3f().set(orientation);
      double sinX = Math.max(-1.0, Math.min(1.0, (double)(-m.m21())));
      double x = Math.asin(sinX);
      double y;
      double z;
      if (Math.abs(sinX) > 0.999999) {
         x = Math.copySign(Math.PI / 2, sinX);
         y = Math.atan2((double)(-m.m02()), (double)m.m00());
         z = 0.0;
      } else {
         y = Math.atan2((double)m.m20(), (double)m.m22());
         z = Math.atan2((double)m.m01(), (double)m.m11());
      }

      return new Vector3f(180.0F - (float)y / (float) (Math.PI / 180.0), -((float)x) / (float) (Math.PI / 180.0), -((float)z) / (float) (Math.PI / 180.0));
   }

   private SceneViewRotation() {
   }
}
