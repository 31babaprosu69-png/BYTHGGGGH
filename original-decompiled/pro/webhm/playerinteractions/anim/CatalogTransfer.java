package pro.webhm.playerinteractions.anim;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CatalogTransfer {
   private final int generation;
   private final int count;
   private int bytes;
   private final Map<Integer, AnimationBundle> pending = new LinkedHashMap<>();

   public CatalogTransfer(int generation, int count) {
      AnimationBundle.require(count >= 0 && count <= 64, "Invalid catalogue count");
      this.generation = generation;
      this.count = count;
   }

   public void add(int generation, int action, byte[] json) {
      AnimationBundle.require(
         generation == this.generation && action >= 7 && this.pending.size() < this.count && !this.pending.containsKey(action), "Unexpected catalogue entry"
      );
      AnimationBundle.require(json.length <= 524288 && (this.bytes += json.length) <= 8388608, "Catalogue exceeds bounds");
      AnimationBundle bundle = AnimationBundle.parse(new String(json, StandardCharsets.UTF_8), "Animation");
      AnimationBundle.require(bundle.action() == action, "Animation content does not match ID");
      this.pending.put(action, bundle);
   }

   public List<AnimationBundle> finish(int generation) {
      AnimationBundle.require(generation == this.generation && this.pending.size() == this.count, "Incomplete catalogue");
      return List.copyOf(this.pending.values());
   }
}
