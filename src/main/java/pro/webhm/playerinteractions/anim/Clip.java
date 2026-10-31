package pro.webhm.playerinteractions.anim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.BoneTransformAdapter;

public final class Clip {
   public static final Gson JSON = new GsonBuilder().registerTypeAdapter(BoneTransform.class, new BoneTransformAdapter()).create();
   public static final String[] NAMES = new String[]{"01", "02_2", "02_1", "test2h"};
   private static final Map<String, Clip> CACHE = new HashMap<>();
   private static volatile Map<Integer, AnimationBundle> serverCustom = Map.of();
   private static volatile Map<Integer, AnimationBundle> clientCustom = Map.of();
   public transient NpcAnimation original;
   public String name;
   public int duration;
   public float sceneOffsetX;
   public float sceneOffsetY;
   public float sceneOffsetZ;
   public float sceneOffsetYaw;
   public boolean bedRelative;
   public List<Clip.Frame> frames = new ArrayList<>();
   public List<Clip.Pet> petPoints = new ArrayList<>();
   public List<Clip.Cue> cues = new ArrayList<>();

   private static AnimationBundle custom(int id) {
      AnimationBundle bundle = serverCustom.get(id);
      return bundle != null ? bundle : clientCustom.get(id);
   }

   public static boolean hasCustom(int id) {
      return custom(id) != null;
   }

   private static Map<Integer, AnimationBundle> index(Collection<AnimationBundle> bundles) {
      HashMap<Integer, AnimationBundle> next = new HashMap<>();

      for (AnimationBundle b : bundles) {
         next.put(b.action(), b);
      }

      return Map.copyOf(next);
   }

   public static void installCustom(Collection<AnimationBundle> bundles) {
      serverCustom = index(bundles);
   }

   public static void installClientCustom(Collection<AnimationBundle> bundles) {
      clientCustom = index(bundles);
   }

   public static synchronized Clip get(int action, boolean initiator) {
      if (action >= 7) {
         AnimationBundle bundle = custom(action);
         if (bundle == null) {
            throw new IllegalArgumentException("Unknown custom animation");
         } else {
            return initiator ? bundle.player() : bundle.npc();
         }
      } else if (action >= 0 && action < NAMES.length) {
         String name = NAMES[action] + (initiator ? "_player" : "_npc");
         return CACHE.computeIfAbsent(name, Clip::load);
      } else {
         throw new IllegalArgumentException("Invalid action");
      }
   }

   private static Clip load(String name) {
      try {
         Clip var3;
         try (InputStream stream = Clip.class.getResourceAsStream("/assets/aot_player_interactions/scenes/" + name + ".json")) {
            if (stream == null) {
               throw new IOException("Missing " + name);
            }

            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var3 = parse(source);
         }

         return var3;
      } catch (IOException var6) {
         throw new IllegalStateException(var6);
      }
   }

   public static Clip parse(String source) {
      Clip clip = (Clip)JSON.fromJson(source, Clip.class);
      if (clip != null && clip.duration >= 1 && clip.frames != null && !clip.frames.isEmpty()) {
         clip.original = NpcAnimation.fromJson(source);
         clip.frames.sort(Comparator.comparingInt(f -> f.tick));
         return clip;
      } else {
         throw new IllegalArgumentException("Empty clip");
      }
   }

   public static float smooth(double value) {
      float t = (float)Math.max(0.0, Math.min(1.0, value));
      return t * t * (3.0F - 2.0F * t);
   }

   public Map<String, BoneTransform> pose(double time) {
      return this.original.sample(time, false);
   }

   public ActorMotion motion(double time) {
      return this.original.sampleMotion(time, false);
   }

   public int pauseTick() {
      return this.petPoints == null ? -1 : this.petPoints.stream().filter(p -> p.petting).mapToInt(p -> p.tick).min().orElse(-1);
   }

   public int joinTick() {
      return this.original.hasPlayerJoin() ? this.original.playerJoinTick : -1;
   }

   public boolean repeatPause(int start, int end) {
      return this.original.touchPoints().stream().anyMatch(p -> p.petting && p.repeat && p.tick >= start && p.tick < end);
   }

   public static class Cue {
      public String kind = "text";
      public String text = "";
      public String source = "";
      public int tick;
      public int length = 60;
      public int offset;
      public int color = 16777215;
      public int screenY = 75;
      public float volume = 1.0F;

      public boolean active(double time) {
         return time >= (double)this.tick && time < (double)(this.tick + this.length);
      }
   }

   public static class Frame {
      public int tick;
      public Map<String, BoneTransform> pose = new HashMap<>();
      public ActorMotion motion;
   }

   public static class Pet {
      public int tick;
      public boolean petting;
      public int mode;
   }
}
