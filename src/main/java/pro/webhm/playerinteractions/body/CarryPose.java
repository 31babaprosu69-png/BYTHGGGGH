package pro.webhm.playerinteractions.body;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.NpcBone;

public final class CarryPose {
   private static final float RECLINE = 55.0F;

   public static Vec3 offset(float yaw) {
      return offset(yaw, 1.0F, 0.0F);
   }

   public static Vec3 offset(float yaw, float modelScale, float positionOffsetY) {
      double scale = Float.isFinite(modelScale) ? (double)Math.max(0.05F, modelScale) : 1.0;
      double tilt = Math.toRadians(55.0);
      double height = 1.05 - scale * (1.501 - 0.75 * Math.cos(tilt)) - (double)(Float.isFinite(positionOffsetY) ? positionOffsetY : 0.0F);
      double side = scale * 0.75 * Math.sin(tilt) - 0.1;
      double a = Math.toRadians((double)yaw);
      return new Vec3(-Math.sin(a) * 0.42 + Math.cos(a) * side, height, Math.cos(a) * 0.42 + Math.sin(a) * side);
   }

   public static Map<String, BoneTransform> apply(Map<String, BoneTransform> original, float weight) {
      float w = Float.isFinite(weight) ? Math.max(0.0F, Math.min(1.0F, weight)) : 0.0F;
      HashMap<String, BoneTransform> result = new HashMap<>(original);
      set(result, NpcBone.ROOT, -55.0F, 0.0F, 0.0F, w);
      set(result, NpcBone.BODY, -5.0F, 0.0F, 0.0F, w);
      set(result, NpcBone.CHEST, 0.0F, 0.0F, 0.0F, w);
      set(result, NpcBone.HEAD, 8.0F, -35.0F, 8.0F, w);

      for (int sign : new int[]{-1, 1}) {
         set(result, sign < 0 ? NpcBone.RIGHT_LEG : NpcBone.LEFT_LEG, -69.0F, (float)(sign * 4), (float)(sign * 3), w);
         set(result, sign < 0 ? NpcBone.RIGHT_SHIN : NpcBone.LEFT_SHIN, 100.0F, 0.0F, 0.0F, w);
         set(result, sign < 0 ? NpcBone.RIGHT_ARM : NpcBone.LEFT_ARM, sign < 0 ? 12.0F : -12.0F, sign < 0 ? -37.0F : 31.0F, sign < 0 ? 71.0F : 20.0F, w);
         set(result, sign < 0 ? NpcBone.RIGHT_FOREARM : NpcBone.LEFT_FOREARM, sign < 0 ? -81.0F : -17.0F, 0.0F, 0.0F, w);
      }

      return result;
   }

   private static void set(Map<String, BoneTransform> p, NpcBone bone, float x, float y, float z, float w) {
      BoneTransform b = p.getOrDefault(bone.id, new BoneTransform()).copy();
      b.rx = b.rx + (x - b.rx) * w;
      b.ry = b.ry + (y - b.ry) * w;
      b.rz = b.rz + (z - b.rz) * w;
      p.put(bone.id, bone.clamped(b));
   }

   private CarryPose() {
   }

   public static void petHead(Map<String, BoneTransform> pose, float age) {
      if (Float.isFinite(age) && !(age < 0.0F) && !(age >= 80.0F)) {
         float blend = Math.max(0.0F, Math.min(1.0F, Math.min(age / 16.0F, (80.0F - age) / 16.0F)));
         blend = blend * blend * (3.0F - 2.0F * blend);
         float stroke = (float)Math.sin((double)Math.max(0.0F, age - 16.0F) * Math.PI / 16.0) * 4.0F;
         set(pose, NpcBone.RIGHT_ARM, -39.0F, -7.0F, 114.0F, blend);
         set(pose, NpcBone.RIGHT_FOREARM, -65.0F + stroke, 0.0F, 0.0F, blend);
      }
   }

   public static Map<String, BoneTransform> walking(Map<String, BoneTransform> original, float weight, float phase, float speed) {
      Map<String, BoneTransform> pose = apply(original, weight);
      if (Float.isFinite(phase) && Float.isFinite(speed)) {
         float amount = Math.max(0.0F, Math.min(1.0F, speed * 2.0F)) * (Float.isFinite(weight) ? Math.max(0.0F, Math.min(1.0F, weight)) : 0.0F);

         for (int side = 0; side < 2; side++) {
            NpcBone leg = side == 0 ? NpcBone.RIGHT_LEG : NpcBone.LEFT_LEG;
            BoneTransform var10000 = pose.get(leg.id);
            var10000.rx = var10000.rx + (float)Math.sin((double)(phase + (float)side * 0.35F)) * amount * 1.2F;
            pose.put(leg.id, leg.clamped(pose.get(leg.id)));
            float sway = (side == 0 ? (float)Math.sin((double)phase) : (float)(0.85 * Math.sin((double)phase + 1.8 + 0.25 * Math.sin((double)phase * 0.43))))
               * amount;
            NpcBone shin = side == 0 ? NpcBone.RIGHT_SHIN : NpcBone.LEFT_SHIN;
            pose.get(shin.id).rx += sway * 6.0F;
            pose.put(shin.id, shin.clamped(pose.get(shin.id)));
         }

         return pose;
      } else {
         return pose;
      }
   }

   public static Map<String, BoneTransform> playerArms() {
      HashMap<String, BoneTransform> pose = new HashMap<>();
      supportArm(pose, NpcBone.RIGHT_ARM, NpcBone.RIGHT_FOREARM, 0.9, 0.34);
      supportArm(pose, NpcBone.LEFT_ARM, NpcBone.LEFT_FOREARM, 1.04, 0.43);
      return pose;
   }

   private static void supportArm(Map<String, BoneTransform> pose, NpcBone arm, NpcBone forearm, double height, double forward) {
      double scale = 0.9375;
      double upper = 0.25 * scale;
      double lower = 0.375 * scale;
      double down = 1.376 * scale - height;
      double cosine = (down * down + forward * forward - upper * upper - lower * lower) / (2.0 * upper * lower);
      double bend = -Math.acos(Math.max(-1.0, Math.min(1.0, cosine)));
      double shoulder = Math.atan2(-forward, down) - Math.atan2(lower * Math.sin(bend), upper + lower * Math.cos(bend));
      set(pose, arm, (float)Math.toDegrees(shoulder), 0.0F, 0.0F, 1.0F);
      set(pose, forearm, (float)Math.toDegrees(bend), 0.0F, 0.0F, 1.0F);
   }
}
