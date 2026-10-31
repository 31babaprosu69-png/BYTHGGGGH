package pro.webhm.playerinteractions.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.body.TouchPose;
import pro.webhm.playerinteractions.npc.BoneTransform;

final class HandHoldPose {
   private static Map<String, BoneTransform> reference(Map<String, BoneTransform> base, boolean left) {
      return TouchPose.handInvitation(base, 1.0F, left ? 1 : -1, true);
   }

   private static HandHoldPose.Rest rest(Vec3 position, float yaw, boolean slim, boolean left) {
      SceneTouchArm arm = new SceneTouchArm(left);
      arm.begin(reference(Map.of(), left), (float)position.x, (float)position.y, (float)position.z, yaw, slim);
      return new HandHoldPose.Rest(new Vector3f(arm.shoulder), new Vector3f(arm.palm));
   }

   static Vector3f target(Vec3 leader, float leaderYaw, boolean leaderSlim, Vec3 follower, float followerYaw, boolean followerSlim) {
      HandHoldPose.Rest a = rest(leader, leaderYaw, leaderSlim, false);
      HandHoldPose.Rest b = rest(follower, followerYaw, followerSlim, true);
      Vector3f target = new Vector3f(a.palm).lerp(b.palm, 0.5F);
      Vector3f axis = new Vector3f(b.shoulder).sub(a.shoulder);
      float distance = axis.length();
      float first = a.shoulder.distance(a.palm);
      float second = b.shoulder.distance(b.palm);
      if (!(distance < 1.0E-5F) && !(distance >= first + second) && !(distance <= Math.abs(first - second))) {
         axis.div(distance);
         float along = (first * first - second * second + distance * distance) / (2.0F * distance);
         Vector3f center = new Vector3f(a.shoulder).fma(along, axis);
         Vector3f radial = new Vector3f(target).sub(center);
         radial.fma(-radial.dot(axis), axis);
         return radial.lengthSquared() < 1.0E-8F
            ? target
            : center.fma((float)Math.sqrt((double)Math.max(0.0F, first * first - along * along)) / radial.length(), radial);
      } else {
         return target;
      }
   }

   static Map<String, BoneTransform> apply(Map<String, BoneTransform> base, Vec3 position, float yaw, boolean slim, boolean left, Vector3f target, float weight) {
      SceneTouchArm arm = new SceneTouchArm(left);
      arm.begin(reference(base, left), (float)position.x, (float)position.y, (float)position.z, yaw, slim);
      arm.solve(target, 180.0F, 1.0F);
      String upper = left ? "left_arm" : "right_arm";
      String lower = left ? "left_forearm" : "right_forearm";
      HashMap<String, BoneTransform> result = new HashMap<>(base);
      Map<String, BoneTransform> blended = arm.blend(base.getOrDefault(upper, new BoneTransform()), base.getOrDefault(lower, new BoneTransform()), weight);
      result.put(upper, blended.get(upper).copy());
      result.put(lower, blended.get(lower).copy());
      return result;
   }

   private HandHoldPose() {
   }

   private static record Rest(Vector3f shoulder, Vector3f palm) {
   }
}
