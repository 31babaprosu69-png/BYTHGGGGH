package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import pro.webhm.playerinteractions.Appearance;
import pro.webhm.playerinteractions.body.BodyPhysics;
import pro.webhm.playerinteractions.npc.BodyData;
import pro.webhm.playerinteractions.npc.BoneTransform;

final class AvatarKinematics {
   private static AvatarRig wide;
   private static AvatarRig slim;
   private static final BodyData WIDE = data(false);
   private static final BodyData SLIM = data(true);
   private static final BodyPhysics STILL = new BodyPhysics();

   private static BodyData data(boolean slim) {
      BodyData d = new BodyData();
      d.slim = slim;
      d.breast = Appearance.defaults();
      return d;
   }

   static Matrix4f shoulder(Map<String, BoneTransform> pose, boolean thin, boolean left, Matrix4f out) {
      if (wide == null) {
         wide = new AvatarRig(false);
         slim = new AvatarRig(true);
      }

      AvatarRig probe = thin ? slim : wide;
      BodyData data = thin ? SLIM : WIDE;
      HashMap<String, BoneTransform> neutral = new HashMap<>(pose);
      String key = left ? "left_arm" : "right_arm";
      BoneTransform arm = neutral.getOrDefault(key, new BoneTransform()).copy();
      arm.rx = arm.ry = arm.rz = 0.0F;
      neutral.put(key, arm);
      probe.configure(data, neutral, 0.0F, STILL, true, true);
      PoseStack stack = new PoseStack();
      probe.touchBone(key, stack);
      return out.set(stack.last().pose());
   }

   private AvatarKinematics() {
   }
}
