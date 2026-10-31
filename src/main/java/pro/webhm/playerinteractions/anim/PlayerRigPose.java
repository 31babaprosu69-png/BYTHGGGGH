package pro.webhm.playerinteractions.anim;

import java.util.HashMap;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class PlayerRigPose {
   public static Map<String, BoneTransform> convert(Map<String, BoneTransform> pose) {
      HashMap<String, BoneTransform> result = new HashMap<>(pose);
      BoneTransform r = result.getOrDefault("root", new BoneTransform()).copy();
      float scale = 0.9375F;
      float pivot = (1.501F - 0.7F / scale) * 16.0F;
      float rad = (float) (Math.PI / 180.0);
      Vector3f shifted = new Quaternionf().rotationZYX(r.rz * rad, r.ry * rad, r.rx * rad).transform(new Vector3f(0.0F, pivot, 0.0F));
      r.shiftX = r.shiftX / scale - shifted.x;
      r.height = r.height / scale - pivot + shifted.y;
      r.shiftZ = r.shiftZ / scale - shifted.z;
      result.put("root", r);
      return result;
   }

   private PlayerRigPose() {
   }
}
