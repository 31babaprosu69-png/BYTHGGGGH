package pro.webhm.playerinteractions.body;

import java.util.HashMap;
import java.util.Map;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.NpcBone;

public final class TouchPose {
   public static Map<String, BoneTransform> apply(Map<String, BoneTransform> original, int zone, int side, float x, float y, float z) {
      if (zone != 0 && zone != 4) {
         Map<String, BoneTransform> result = new HashMap<>(original);
         String prefix = side < 0 ? "right_" : "left_";
         if (zone == 1 || zone == 2) {
            float lift = Math.max(0.0F, -y);
            float bend = lift * (float)(zone == 2 ? 46 : 24) + Math.abs(x) * 8.0F + Math.max(0.0F, -z) * 12.0F;
            rotate(result, prefix + "arm", z * 16.0F - lift * 30.0F, 0.0F, -x * 23.0F);
            rotate(result, prefix + "forearm", -bend, 0.0F, 0.0F);
         } else if (zone == 5 || zone == 6) {
            float lift = Math.max(0.0F, -y);
            rotate(result, prefix + "leg", z * 15.0F - lift * 36.0F, 0.0F, -x * 18.0F);
            rotate(result, prefix + "shin", lift * (float)(zone == 6 ? 58 : 38) + Math.abs(x) * 8.0F + Math.max(0.0F, z) * 12.0F, 0.0F, 0.0F);
         } else if (zone == 3) {
            rotate(result, "body", y * 2.0F - z * 2.0F, 0.0F, x * 3.0F);
         }

         return result;
      } else {
         return original;
      }
   }

   public static Map<String, BoneTransform> pet(Map<String, BoneTransform> original, float weight, float x, float y) {
      weight = Math.max(0.0F, Math.min(1.0F, weight));
      if (weight < 0.001F) {
         return original;
      } else {
         HashMap<String, BoneTransform> result = new HashMap<>(original);
         toward(result, "body", 5.0F, 0.0F, 0.0F, weight);
         toward(result, "chest", -3.0F, 0.0F, 0.0F, weight);

         for (int side : new int[]{-1, 1}) {
            String prefix = side < 0 ? "right_" : "left_";
            toward(result, prefix + "arm", -32.0F + y * 2.0F, (float)(side * 12), (float)(side * 10), weight);
            toward(result, prefix + "forearm", -128.0F + (float)side * x * 3.0F, 0.0F, (float)(-side * 12), weight);
         }

         return result;
      }
   }

   public static Map<String, BoneTransform> startled(Map<String, BoneTransform> original, float weight, float flinch) {
      weight = Math.max(0.0F, Math.min(1.0F, weight));
      if (weight < 0.001F) {
         return original;
      } else {
         HashMap<String, BoneTransform> result = new HashMap<>(original);
         rotate(result, "body", -5.0F * weight, 0.0F, 0.0F);
         rotate(result, "chest", flinch * 2.0F, 0.0F, 0.0F);

         for (int side : new int[]{-1, 1}) {
            String prefix = side < 0 ? "right_" : "left_";
            rotate(result, prefix + "arm", -12.0F * weight - flinch * 4.0F, 0.0F, (float)(side * 5) * weight);
            toward(result, prefix + "forearm", -128.0F, 0.0F, 0.0F, weight);
         }

         return result;
      }
   }

   public static Map<String, BoneTransform> handInvitation(Map<String, BoneTransform> original, float weight, int side, boolean walking) {
      weight = Math.max(0.0F, Math.min(1.0F, weight));
      if (weight < 0.001F) {
         return original;
      } else {
         HashMap<String, BoneTransform> result = new HashMap<>(original);
         String arm = side < 0 ? "right_" : "left_";
         if (!walking) {
            rotate(result, "body", 6.0F * weight, 0.0F, (float)(side * 3) * weight);
            rotate(result, "head", 4.0F * weight, 0.0F, (float)(-side * 5) * weight);
         }

         toward(result, arm + "arm", walking ? -12.0F : -25.0F, 0.0F, walking ? (float)(-side * 12) : (float)(-side * 8), weight);
         toward(result, arm + "forearm", walking ? -12.0F : -65.0F, 0.0F, 0.0F, weight);
         return result;
      }
   }

   private static void toward(Map<String, BoneTransform> pose, String bone, float x, float y, float z, float weight) {
      BoneTransform t = pose.getOrDefault(bone, new BoneTransform()).copy();
      t.rx = t.rx + (x - t.rx) * weight;
      t.ry = t.ry + (y - t.ry) * weight;
      t.rz = t.rz + (z - t.rz) * weight;
      pose.put(bone, NpcBone.byId(bone).clamped(t));
   }

   private static void rotate(Map<String, BoneTransform> pose, String bone, float x, float y, float z) {
      BoneTransform t = pose.getOrDefault(bone, new BoneTransform()).copy();
      t.rx += x;
      t.ry += y;
      t.rz += z;

      for (NpcBone b : NpcBone.values()) {
         if (b.id.equals(bone)) {
            pose.put(bone, b.clamped(t));
            return;
         }
      }
   }
}
