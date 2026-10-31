package pro.webhm.playerinteractions.anim;

import java.util.HashMap;
import java.util.Map;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.NpcEquipment;

public class Keyframe {
   public int tick;
   public NpcEquipment items;
   public ActorMotion motion;
   public Map<String, BoneTransform> pose = new HashMap<>();

   public Keyframe() {
   }

   public Keyframe(int tick, Map<String, BoneTransform> pose) {
      this.tick = tick;
      this.pose = new HashMap<>();
      pose.forEach((k, v) -> this.pose.put(k, v.copy()));
   }
}
