package pro.webhm.playerinteractions.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.NpcBone;

public record AnimationBundle(int action, String name, String json, Clip player, Clip npc) {
   public static final int MAX_BYTES = 524288;
   public static final int MAX_DURATION = 4800;

   public static AnimationBundle parse(String source, String fallback) {
      require(source.getBytes(StandardCharsets.UTF_8).length <= 524288, "File exceeds 512 KiB");

      JsonObject root;
      try {
         JsonReader reader = new JsonReader(new StringReader(source));

         try {
            reader.setLenient(false);
            JsonElement parsed = read(reader, 0, new int[]{0});
            require(reader.peek() == JsonToken.END_DOCUMENT && parsed.isJsonObject(), "Expected one JSON object");
            root = parsed.getAsJsonObject();
         } catch (Throwable var10) {
            try {
               reader.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }

            throw var10;
         }

         reader.close();
      } catch (IllegalStateException | IOException var11) {
         throw new IllegalArgumentException("Invalid JSON", var11);
      }

      require(!root.has("format") || integer(root.get("format")) == 1, "Unsupported format");
      String name = root.has("name") ? root.get("name").getAsString() : fallback;
      require(
         name != null && !name.isBlank() && name.length() <= 64 && name.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 167),
         "Name must contain 1-64 plain characters"
      );
      root.addProperty("name", name);
      root.addProperty("format", 1);
      Clip player = track(root.get("player"));
      Clip npc = track(root.get("npc"));
      int duration = Math.max(player.duration, npc.duration);
      require(npc.joinTick() < duration, "Join tick is outside the scene");
      require(npc.pauseTick() < 0 || npc.pauseTick() >= npc.joinTick(), "Touch pause must follow the player join");
      if (npc.original.hasSceneLoop()) {
         int start = npc.original.sceneLoopStart;
         int end = npc.original.sceneLoopEnd;
         require(npc.joinTick() < end, "Player must join before the loop endpoint");
         require(end <= player.duration, "Player track ends before the loop endpoint");
         require(
            !player.original.hasSceneLoop() || player.original.sceneLoopStart == start && player.original.sceneLoopEnd == end,
            "Both role tracks must use the same loop markers"
         );
      } else {
         require(!player.original.hasSceneLoop(), "Loop markers must be present on the npc track");
      }

      String json = root.toString();
      require(json.getBytes(StandardCharsets.UTF_8).length <= 524288, "Normalized file exceeds 512 KiB");
      return new AnimationBundle(id(json), name, json, player, npc);
   }

   private static Clip track(JsonElement value) {
      require(value != null && value.isJsonObject(), "Both player and npc objects are required");
      JsonObject obj = value.getAsJsonObject();
      int duration = integer(obj.get("duration"));
      require(duration >= 1 && duration <= 4800, "Duration must be 1-4800 ticks");
      require(obj.has("frames") && obj.get("frames").isJsonArray(), "Missing frames");
      JsonArray frames = obj.getAsJsonArray("frames");
      require(!frames.isEmpty() && frames.size() <= 2048, "Expected 1-2048 frames");
      Set<Integer> ticks = new HashSet<>();
      Set<String> bones = new HashSet<>();

      for (NpcBone bone : NpcBone.values()) {
         bones.add(bone.id);
      }

      for (JsonElement element : frames) {
         require(element.isJsonObject(), "Invalid frame");
         JsonObject frame = element.getAsJsonObject();
         int tick = integer(frame.get("tick"));
         require(tick >= 0 && tick <= duration && ticks.add(tick), "Frame ticks must be unique and within duration");
         require(frame.has("pose") && frame.get("pose").isJsonObject(), "Missing frame pose");

         for (Entry<String, JsonElement> bone : frame.getAsJsonObject("pose").entrySet()) {
            require(bones.contains(bone.getKey()), "Unknown bone: " + bone.getKey());
            JsonElement transform = bone.getValue();
            require(transform.isJsonArray() || transform.isJsonObject(), "Invalid bone transform");
            if (transform.isJsonArray()) {
               require(transform.getAsJsonArray().size() <= 9, "Invalid bone array");
            }

            for (JsonElement number : transform.isJsonArray() ? transform.getAsJsonArray() : transform.getAsJsonObject().asMap().values()) {
               require(number.isJsonPrimitive() && number.getAsJsonPrimitive().isNumber() && Math.abs(number.getAsDouble()) <= 4096.0, "Invalid bone value");
            }
         }
      }

      int loopStart = obj.has("sceneLoopStart") ? integer(obj.get("sceneLoopStart")) : -1;
      int loopEnd = obj.has("sceneLoopEnd") ? integer(obj.get("sceneLoopEnd")) : -1;
      require(loopStart == -1 && loopEnd == -1 || loopStart >= 0 && loopEnd > loopStart && loopEnd <= duration, "Invalid scene loop markers");
      Clip clip = Clip.parse(obj.toString());
      require(clip.original.frames.size() == frames.size() && clip.original.duration <= 4800, "Invalid companion track");

      for (Clip.Frame frame : clip.frames) {
         if (frame.motion != null) {
            require(
               Math.abs(frame.motion.x) <= 8.0F
                  && Math.abs(frame.motion.y) <= 8.0F
                  && Math.abs(frame.motion.z) <= 8.0F
                  && Math.abs(frame.motion.yaw) <= 3600.0F,
               "Motion exceeds scene bounds"
            );
            require(Float.isFinite(frame.motion.groundOffset()), "Invalid ground offset");
         }
      }

      require(
         Math.abs(clip.sceneOffsetX) <= 8.0F
            && Math.abs(clip.sceneOffsetY) <= 8.0F
            && Math.abs(clip.sceneOffsetZ) <= 8.0F
            && Math.abs(clip.sceneOffsetYaw) <= 3600.0F,
         "Scene origin exceeds bounds"
      );
      require(clip.original.touchPoints().stream().filter(p -> p.petting).count() <= 1L, "Only one touch pause is supported");
      if (obj.has("playerJoinTick")) {
         int join = integer(obj.get("playerJoinTick"));
         require(join >= -1 && join < duration, "Invalid playerJoinTick");
      }

      require(
         (!obj.has("sceneGuest") || obj.get("sceneGuest").isJsonNull()) && (!obj.has("sceneCopy") || obj.get("sceneCopy").isJsonNull()),
         "Extra-actor scenes are not supported yet"
      );

      for (NpcAnimation.FaceKey key : clip.original.faceKeys) {
         if (key != null) {
            require(Float.isFinite(key.headYaw) && Float.isFinite(key.headPitch), "Invalid head direction");
         }
      }

      for (Clip.Frame framex : clip.frames) {
         for (double time : new double[]{(double)framex.tick, Math.max(0.0, (double)framex.tick - 0.5)}) {
            for (BoneTransform bone : clip.pose(time).values()) {
               require(
                  Float.isFinite(bone.rx)
                     && Float.isFinite(bone.ry)
                     && Float.isFinite(bone.rz)
                     && Float.isFinite(bone.height)
                     && Float.isFinite(bone.shiftX)
                     && Float.isFinite(bone.shiftZ),
                  "Invalid sampled pose"
               );
            }

            ActorMotion motion = clip.motion(time);
            require(
               Float.isFinite(motion.x)
                  && Float.isFinite(motion.y)
                  && Float.isFinite(motion.z)
                  && Float.isFinite(motion.yaw)
                  && Float.isFinite(motion.groundOffset()),
               "Invalid sampled motion"
            );
         }
      }

      clip.duration = clip.original.duration;
      clip.petPoints = new ArrayList<>();

      for (ScenePetPoint point : clip.original.touchPoints()) {
         if (point.petting) {
            Clip.Pet pet = new Clip.Pet();
            pet.tick = point.tick;
            pet.mode = point.mode;
            pet.petting = true;
            clip.petPoints.add(pet);
         }
      }

      if (clip.cues == null) {
         clip.cues = new ArrayList<>();
      }

      clip.cues.removeIf(c -> c == null || c.kind == null || !c.kind.equals("text") && !bundled(c.source));

      for (Clip.Cue cue : clip.cues) {
         require(
            cue.tick >= 0
               && cue.tick < 4800
               && cue.length > 0
               && cue.length <= 4800
               && cue.text != null
               && cue.text.length() <= 4096
               && Float.isFinite(cue.volume),
            "Invalid caption or sound cue"
         );
         cue.volume = Math.max(0.0F, Math.min(1.0F, cue.volume));
      }

      return clip;
   }

   private static boolean bundled(String source) {
      return source != null && source.matches("[a-f0-9]{64}\\.mp3") ? Clip.class.getResource("/assets/aot_player_interactions/media/" + source) != null : false;
   }

   private static int integer(JsonElement e) {
      require(e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber(), "Expected integer");
      double d = e.getAsDouble();
      require(d == Math.rint(d) && d >= -2.1474836E9F && d <= 2.147483647E9, "Expected integer");
      return (int)d;
   }

   private static JsonElement read(JsonReader r, int depth, int[] nodes) throws IOException {
      require(depth <= 24 && ++nodes[0] <= 100000, "JSON is too complex");
      switch (r.peek()) {
         case BEGIN_OBJECT:
            JsonObject o = new JsonObject();
            r.beginObject();

            while (r.hasNext()) {
               String key = r.nextName();
               require(!o.has(key), "Duplicate JSON key");
               o.add(key, read(r, depth + 1, nodes));
            }

            r.endObject();
            return o;
         case BEGIN_ARRAY:
            JsonArray a = new JsonArray();
            r.beginArray();

            while (r.hasNext()) {
               a.add(read(r, depth + 1, nodes));
            }

            r.endArray();
            return a;
         case STRING:
            return new JsonPrimitive(r.nextString());
         case NUMBER:
            String n = r.nextString();
            double d = Double.parseDouble(n);
            require(Double.isFinite(d) && Math.abs(d) <= 1.0E10, "Non-finite or excessive number");
            return new JsonPrimitive(new BigDecimal(n));
         case BOOLEAN:
            return new JsonPrimitive(r.nextBoolean());
         case NULL:
            r.nextNull();
            return JsonNull.INSTANCE;
         default:
            throw new IllegalArgumentException("Unexpected JSON token");
      }
   }

   private static int id(String json) {
      try {
         byte[] hash = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
         return 7 + (ByteBuffer.wrap(hash).getInt() & 2147483647) % 2147483640;
      } catch (NoSuchAlgorithmException var2) {
         throw new IllegalStateException(var2);
      }
   }

   static void require(boolean ok, String message) {
      if (!ok) {
         throw new IllegalArgumentException(message);
      }
   }
}
